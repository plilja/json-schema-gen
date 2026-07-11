package se.plilja.jsonschemagen.internal.generator;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.model.SchemaDocument;

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

    /**
     * One {@link JsonGenerator} per {@link Schema} instance. Identity-keyed so
     * that all call sites reaching the same definition share phase state — the
     * boundary-value cycle advances globally instead of restarting per caller.
     */
    private final Map<Schema, JsonGenerator> generatorCache = new IdentityHashMap<>();

    /**
     * Flips the context into minimal mode so recursive generators collapse
     * to required-only / empty containers.
     */
    static final int GLOBAL_REF_SOFT_DEPTH = 2;

    /**
     * Ceiling for required-field recursion that can never bottom out —
     * beyond this depth the schema is unsatisfiable.
     */
    static final int GLOBAL_REF_HARD_DEPTH = 4;

    /**
     * Number of {@code $ref} expansions currently on the call stack — across
     * both {@link RefGenerator} and {@code allOf} branch resolution, which
     * carries the same unbounded-recursion risk. Drives minimal-mode: when
     * this reaches the soft depth limit, downstream generators collapse to
     * their smallest valid form so recursion terminates.
     */
    private int globalRefDepth;

    GeneratorContext(SchemaDocument document, Random random) {
        this.document = document;
        this.random = random;
    }

    public Random random() {
        return random;
    }

    boolean isMinimal() {
        return globalRefDepth >= GLOBAL_REF_SOFT_DEPTH;
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
