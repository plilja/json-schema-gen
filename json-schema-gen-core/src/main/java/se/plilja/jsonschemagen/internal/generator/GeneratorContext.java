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
     * Minimal-generation mode flag. {@link RefGenerator} flips this on when
     * expanding past the soft recursion limit so containers downstream collapse
     * to their smallest valid form and the recursion terminates.
     */
    private boolean minimal;

    GeneratorContext(SchemaDocument document, Random random) {
        this.document = document;
        this.random = random;
    }

    public Random random() {
        return random;
    }

    boolean isMinimal() {
        return minimal;
    }

    void enterMinimal() {
        minimal = true;
    }

    void exitMinimal() {
        minimal = false;
    }

    JsonGenerator generatorFor(Schema schema) {
        return generatorCache.computeIfAbsent(schema, s -> new JsonGenerator(s, this));
    }

    JsonGenerator generatorForRef(String ref) {
        var target = document.resolveRef(ref);
        if (target == null) {
            throw new IllegalArgumentException("Unresolved $ref: " + ref);
        }
        return generatorFor(target);
    }
}
