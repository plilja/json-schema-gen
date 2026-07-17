package io.github.gjuton.internal.generator;

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

    /**
     * The {@code $ref} expansion ceilings for the three presets, and the single
     * source of truth for the depths the public API exposes. A soft ceiling is
     * the depth at which recursive structures collapse to their smallest valid
     * form so generation terminates; a hard ceiling is the depth beyond which a
     * still-recursing {@code $ref} is treated as unsatisfiable.
     */
    public static final int DEFAULT_REF_SOFT_DEPTH = 3;
    public static final int DEFAULT_REF_HARD_DEPTH = 4;
    public static final int SHALLOW_REF_SOFT_DEPTH = 1;
    public static final int SHALLOW_REF_HARD_DEPTH = 2;
    public static final int DEEP_REF_SOFT_DEPTH = 5;
    public static final int DEEP_REF_HARD_DEPTH = 8;

    public GeneratorConfig {
        producers = Map.copyOf(producers);
    }

    /**
     * The configuration used when the caller sets no options — exhaustive
     * boundary-value generation, no synthesized extra properties, no value
     * overrides, and the default {@code $ref} depth limits.
     */
    static GeneratorConfig defaults() {
        return new GeneratorConfig(false, false, DEFAULT_REF_SOFT_DEPTH, DEFAULT_REF_HARD_DEPTH, Map.of());
    }
}
