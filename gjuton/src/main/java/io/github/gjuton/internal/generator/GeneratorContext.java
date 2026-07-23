package io.github.gjuton.internal.generator;

import io.github.gjuton.internal.model.Schema;
import io.github.gjuton.internal.model.SchemaDocument;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Shared mutable state for a single generation run.
 *
 * <p>A single instance is created at the root and threaded through the
 * entire generator tree. It provides the shared random source, resolves
 * {@code $ref} targets, and ensures that generators reaching the same
 * schema definition share phase state rather than restarting independently.
 */
public final class GeneratorContext {

    private static final int NOVELTY_WINDOW_SIZE = 5;

    /**
     * Upper bound on {@link #mergedSchemaCache}'s size. Bounds memory for
     * schemas whose {@code anyOf}/{@code oneOf} random-subset picks can
     * produce a large number of distinct branch combinations over a long
     * generation run.
     */
    static final int MERGED_SCHEMA_CACHE_CAPACITY = 256;

    /**
     * The parsed document, used to look up {@code $ref} targets.
     */
    private final SchemaDocument document;

    private final Random random;

    private final GeneratorConfig config;

    /**
     * One {@link JsonGenerator} per {@link Schema} instance. Identity-keyed so
     * that all call sites reaching the same definition share phase state — the
     * boundary-value cycle advances globally instead of restarting per caller.
     */
    private final Map<Schema, JsonGenerator> generatorCache = new IdentityHashMap<>();

    /**
     * Number of {@code $ref} expansions currently on the call stack — across
     * both {@link RefGenerator} and {@code allOf} branch resolution, which
     * carries the same unbounded-recursion risk. Drives minimal-mode: when
     * this reaches the soft depth limit, downstream generators collapse to
     * their smallest valid form so recursion terminates.
     */
    private int globalRefDepth;

    /**
     * JSON path of the position currently being generated, e.g. {@code $.a[0]}.
     * Object and array generators extend it as they descend into a child and
     * restore it on the way back up, so {@link #currentOverride} can look up
     * a producer registered for exactly this position.
     */
    private final StringBuilder currentPath = new StringBuilder("$");

    /**
     * Override values already produced during the current generation run, keyed
     * by path. A validate-and-retry parent may regenerate the same subtree
     * several times within one run; memoizing here keeps each producer to a
     * single invocation per run and pins its value across those retries. Reset
     * by {@link #startRun}.
     */
    private final Map<String, Object> producedThisRun = new HashMap<>();

    /**
     * Whether each of the most recent completed generation runs produced at
     * least one novel value, oldest first, capped at {@link #NOVELTY_WINDOW_SIZE}.
     */
    private final ArrayDeque<Boolean> noveltyWindow = new ArrayDeque<>();

    /**
     * Deliberate-value indices each generator has emitted at least once, across
     * all runs so far. Identity-keyed so generators sharing phase state (see
     * {@link #generatorCache}) also share their novelty history.
     */
    private final Map<Generator<?>, BitSet> noveltyBits = new IdentityHashMap<>();

    /**
     * Whether each of a generator's most recent completed visits committed a
     * deliberate value it had not already emitted, oldest first, capped at
     * {@link #NOVELTY_WINDOW_SIZE}. Identity-keyed so generators sharing phase
     * state (see {@link #generatorCache}) also share this history, mirroring
     * {@link #noveltyBits}. Unlike {@link #noveltyWindow}, a generator not
     * visited in a given run gets no entry for that run.
     */
    private final Map<Generator<?>, ArrayDeque<Boolean>> noveltyWindowByGenerator = new IdentityHashMap<>();

    /**
     * Merged schemas already computed, keyed by their input branch list, so
     * repeated merges of the same branches return the same {@link Schema}
     * instance. Capped at {@link #MERGED_SCHEMA_CACHE_CAPACITY}, least
     * recently used first; an eviction here also drops the evicted schema's
     * generator and novelty history so they don't outlive it.
     */
    private final Map<Set<Schema>, Schema> mergedSchemaCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Set<Schema>, Schema> eldest) {
            if (size() <= MERGED_SCHEMA_CACHE_CAPACITY) {
                return false;
            }
            var evictedGenerator = generatorCache.remove(eldest.getValue());
            if (evictedGenerator != null) {
                noveltyBits.remove(evictedGenerator.delegate());
                noveltyWindowByGenerator.remove(evictedGenerator.delegate());
            }
            return true;
        }
    };

    /**
     * Visits registered so far in the current run, in order, so a discarded
     * candidate's visits can be undone by {@link #rollback}.
     */
    private final List<VisitJournalEntry> visitJournal = new ArrayList<>();

    private record VisitJournalEntry(Generator<?> generator, int index) {
    }

    GeneratorContext(SchemaDocument document, Random random) {
        this(document, random, GeneratorConfig.defaults());
    }

    GeneratorContext(SchemaDocument document, Random random, GeneratorConfig config) {
        this.document = document;
        this.random = random;
        this.config = config;
    }

    public Random random() {
        return random;
    }

    /**
     * The caller-imposed bounds narrowing generated values; every kind is unset
     * when the caller registered no constraints.
     */
    public ValueConstraints constraints() {
        return config.constraints();
    }

    boolean isRandomOnly() {
        return config.randomOnly();
    }

    boolean generateAdditionalProperties() {
        return config.generateAdditionalProperties();
    }

    /**
     * Ceiling for required-field recursion that can never bottom out —
     * beyond this depth the schema is unsatisfiable.
     */
    int refHardDepth() {
        return config.refHardDepth();
    }

    boolean isMinimal() {
        return globalRefDepth >= config.refSoftDepth();
    }

    int getGlobalRefDepth() {
        return globalRefDepth;
    }

    void incrementGlobalRefDepth() {
        globalRefDepth++;
    }

    void decrementGlobalRefDepth() {
        globalRefDepth--;
    }

    JsonGenerator generatorFor(Schema schema) {
        return generatorCache.computeIfAbsent(schema, s -> new JsonGenerator(s, this));
    }

    /**
     * Resets per-run generation state. Must be called once at the start of a
     * full generation run so that each registered producer is consulted afresh
     * for that run.
     */
    void startRun() {
        producedThisRun.clear();
        visitJournal.clear();
    }

    /**
     * Records that {@code generator} visited its deliberate value at
     * {@code index}.
     */
    void registerVisit(Generator<?> generator, int index) {
        visitJournal.add(new VisitJournalEntry(generator, index));
    }

    /**
     * Marks the current point in the run's visit history, to later
     * {@link #rollback} to if the candidate being generated is discarded.
     */
    int checkpoint() {
        return visitJournal.size();
    }

    /**
     * Discards every visit registered since {@code mark} was taken, as if
     * they had never happened.
     */
    void rollback(int mark) {
        visitJournal.subList(mark, visitJournal.size()).clear();
    }

    /**
     * Finalizes the current generation run, updating per-generator and global
     * novelty scores based on the visits registered since {@link #startRun}.
     * Must be called once at the end of every full generation run.
     */
    void completeRun() {
        boolean runHasNovelty = false;
        var novelByGenerator = new IdentityHashMap<Generator<?>, Boolean>();
        for (var entry : visitJournal) {
            var bits = noveltyBits.computeIfAbsent(entry.generator(), ignored -> new BitSet());
            boolean isNovel = !bits.get(entry.index());
            if (isNovel) {
                bits.set(entry.index());
                runHasNovelty = true;
            }
            novelByGenerator.merge(entry.generator(), isNovel, Boolean::logicalOr);
        }
        novelByGenerator.forEach((generator, isNovel) -> {
            var window = noveltyWindowByGenerator.computeIfAbsent(generator, ignored -> new ArrayDeque<>());
            if (window.size() == NOVELTY_WINDOW_SIZE) {
                window.removeFirst();
            }
            window.addLast(isNovel);
        });
        if (noveltyWindow.size() == NOVELTY_WINDOW_SIZE) {
            noveltyWindow.removeFirst();
        }
        noveltyWindow.addLast(runHasNovelty);
        visitJournal.clear();
    }

    /**
     * Merges {@code schemas} into one, returning the same {@link Schema}
     * instance for equal lists so callers that merge the same combination
     * repeatedly (per-call branch selection in {@code oneOf}/{@code anyOf})
     * get back a schema whose generator and novelty history persist across
     * calls instead of restarting from scratch every time.
     */
    Schema mergedSchema(List<Schema> schemas) {
        if (schemas.size() == 1) {
            return schemas.get(0);
        }
        var key = Set.copyOf(schemas);
        // get()/put() rather than computeIfAbsent(): access-order LRU eviction
        // only reorders on get(), not on a computeIfAbsent cache hit.
        var cached = mergedSchemaCache.get(key);
        if (cached != null) {
            return cached;
        }
        var merged = SchemaMerger.merge(schemas);
        mergedSchemaCache.put(key, merged);
        return merged;
    }

    /**
     * The fraction of the most recent completed generation runs (up to
     * {@link #NOVELTY_WINDOW_SIZE}) that produced at least one value not
     * already emitted by an earlier run. {@code 1.0} before any run has
     * completed.
     */
    public double noveltyScore() {
        if (noveltyWindow.isEmpty()) {
            return 1.0;
        }
        long novelRuns = noveltyWindow.stream().filter(Boolean::booleanValue).count();
        return (double) novelRuns / noveltyWindow.size();
    }

    /**
     * The fraction of {@code generator}'s own most recent completed visits
     * (up to {@link #NOVELTY_WINDOW_SIZE}) that committed a deliberate value
     * it had not already emitted. Empty if {@code generator} has never been
     * visited in a completed run.
     */
    Optional<Double> noveltyScore(Generator<?> generator) {
        var window = noveltyWindowByGenerator.get(generator);
        if (window == null || window.isEmpty()) {
            return Optional.empty();
        }
        long novelRuns = window.stream().filter(Boolean::booleanValue).count();
        return Optional.of((double) novelRuns / window.size());
    }

    /**
     * Returns the caller's override for the position at the current path, or
     * {@code null} if no producer is registered there.
     *
     * <p>Path-based producers are checked first; if none matches and the current
     * position is an object property (not an array element or the root),
     * name-based producers are checked against the property name.
     *
     * <p>Within one run (see {@link #startRun}) a producer is consulted at most
     * once per memoization key. Path-based producers are keyed by path, so
     * retries at the same position see the same value. Name-based producers are
     * keyed by property name, so every position with the same name shares one
     * value per run — the property means the same thing wherever it appears.
     */
    Object currentOverride() {
        var path = currentPath.toString();
        var producer = config.producers().get(path);
        if (producer != null) {
            return producedThisRun.computeIfAbsent(path, ignored -> new OverriddenValue(producer.get()));
        }

        // The path string doesn't distinguish position kinds (object property vs
        // array element vs root), so recover that from its shape: paths ending
        // with ']' are array elements, paths with no '.' are the root — only the
        // rest are object properties where name-based matching applies.
        if (!config.nameProducers().isEmpty() && path.charAt(path.length() - 1) != ']') {
            int lastDot = path.lastIndexOf('.');
            if (lastDot >= 0) {
                var propertyName = path.substring(lastDot + 1);
                var nameProducer = config.nameProducers().get(propertyName);
                if (nameProducer != null) {
                    return producedThisRun.computeIfAbsent(
                            propertyName, ignored -> new OverriddenValue(nameProducer.get()));
                }
            }
        }

        return null;
    }

    /**
     * Descends into the child position reached by appending {@code pathSegment}
     * to the current path (e.g. {@code ".name"} or {@code "[0]"}). Every call
     * must be paired with an {@link #exitPath} for the same segment once the
     * child has been generated.
     */
    void enterPath(String pathSegment) {
        currentPath.append(pathSegment);
    }

    /**
     * Ascends out of the child entered with {@link #enterPath}, restoring the
     * path to what it was before. {@code pathSegment} must match the paired
     * {@link #enterPath} call.
     */
    void exitPath(String pathSegment) {
        currentPath.setLength(currentPath.length() - pathSegment.length());
    }

    /**
     * Resolves a {@code $ref} string to the {@link Schema} it points at.
     *
     * @throws IllegalArgumentException if the ref cannot be resolved
     */
    Schema resolveRef(String ref) {
        var target = document.resolveRef(ref);
        if (target == null) {
            throw new IllegalArgumentException("Unresolved $ref: " + ref);
        }
        return target;
    }

}
