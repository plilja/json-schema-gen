package io.github.gjuton.internal.generator;

import io.github.gjuton.internal.model.Schema;
import io.github.gjuton.internal.model.SchemaDocument;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Shared mutable state for a single generation run.
 *
 * <p>A single instance is created at the root and threaded through the
 * entire generator tree. It provides the shared random source, resolves
 * {@code $ref} targets, and ensures that generators reaching the same
 * schema definition share phase state rather than restarting independently.
 */
public final class GeneratorContext {

    /** The parsed document, used to look up {@code $ref} targets. */
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

    /**
     * The {@code $ref} depth at which generation collapses to minimal form. A
     * generator only ever reached at or beyond this depth never emits its
     * deliberate values, so coverage excludes it.
     */
    int refSoftDepth() {
        return config.refSoftDepth();
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
    }

    /**
     * Returns the caller's override for the position at the current path, or
     * {@code null} if no producer is registered there.
     *
     * <p>Within one run (see {@link #startRun}) a producer is consulted at most
     * once per path: the first query at a path yields its value and every later
     * query at that same path returns the same value, so a validate-and-retry
     * parent that regenerates the subtree does not re-invoke it. The result,
     * when present, is wrapped so downstream consumers can recognise it as
     * caller-supplied.
     */
    Object currentOverride() {
        var path = currentPath.toString();
        var producer = config.producers().get(path);
        if (producer == null) {
            return null;
        }
        return producedThisRun.computeIfAbsent(path, ignored -> new OverriddenValue(producer.get()));
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
