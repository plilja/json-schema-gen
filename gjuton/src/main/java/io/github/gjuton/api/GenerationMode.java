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
     * bugs in the system under test. This is the default.
     */
    EXHAUSTIVE,

    /**
     * Emit only random valid values, skipping the boundary-value cycle.
     * Faster and less repetitive when boundary coverage is not needed.
     */
    RANDOM_ONLY
}
