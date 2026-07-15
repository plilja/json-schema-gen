package se.plilja.jsonschemagen.internal.generator;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Generation options that alter how values are produced, threaded from the
 * public API into the generator tree via {@link GeneratorContext}.
 *
 * <p>This is the internal counterpart of the public configuration surface; it
 * carries only the primitive values the generators need, so the generator
 * package stays free of {@code api} types.
 *
 * @param randomOnly                    emit only random values, skipping the
 *                                      boundary-value cycle
 * @param generateAdditionalProperties  add random extra properties to objects
 *                                      wherever the schema permits them
 * @param refSoftDepth                  {@code $ref} depth at which generation
 *                                      collapses to minimal form
 * @param refHardDepth                  {@code $ref} depth beyond which the
 *                                      schema is treated as unsatisfiable
 * @param producers                     value overrides keyed by JSON path; the
 *                                      supplier at a path yields a ready
 *                                      value-tree node to place there instead
 *                                      of generating one
 */
public record GeneratorConfig(
        boolean randomOnly,
        boolean generateAdditionalProperties,
        int refSoftDepth,
        int refHardDepth,
        Map<String, Supplier<Object>> producers) {

    public GeneratorConfig {
        producers = Map.copyOf(producers);
    }

    /**
     * The configuration used when the caller sets no options — exhaustive
     * boundary-value generation, no synthesized extra properties, no value
     * overrides, and the default {@code $ref} depth limits (soft 2, hard 4).
     */
    static GeneratorConfig defaults() {
        return new GeneratorConfig(false, false, 2, 4, Map.of());
    }
}
