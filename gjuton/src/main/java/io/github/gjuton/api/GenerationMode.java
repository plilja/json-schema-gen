package io.github.gjuton.api;

/**
 * Controls how {@link Gjuton} chooses values across successive
 * {@link Gjuton#generate()} calls.
 *
 * <p>New modes may be added in future releases; consumers switching on this
 * enum should handle unrecognised values defensively.
 */
public enum GenerationMode {

    /**
     * Emit trouble-prone boundary values first (empty string, min/max, zero,
     * and similar), then random valid values. Maximises the chance of exposing
     * bugs in the system under test. Opt in with
     * {@link Gjuton#withGenerationMode(GenerationMode)} for boundary-value or
     * property-style testing.
     */
    EXHAUSTIVE,

    /**
     * Emit only random valid values, skipping the trouble-prone boundary values.
     * Faster and less repetitive when boundary coverage is not needed. This is
     * the default.
     */
    RANDOM
}
