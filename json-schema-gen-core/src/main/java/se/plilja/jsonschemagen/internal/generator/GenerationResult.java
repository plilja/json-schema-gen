package se.plilja.jsonschemagen.internal.generator;

/**
 * Outcome of a single {@link PhaseGenerator} phase: either a
 * {@link Present} value or a {@link Skip} signalling that the phase
 * could not produce a value and the next phase should be tried.
 */
public sealed interface GenerationResult<R> {

    static <R> GenerationResult<R> result(R value) {
        return new Present<>(value);
    }

    @SuppressWarnings("unchecked")
    static <R> GenerationResult<R> skip() {
        return (GenerationResult<R>) Skip.INSTANCE;
    }

    record Present<R>(R value) implements GenerationResult<R> {
    }

    record Skip<R>() implements GenerationResult<R> {
        private static final Skip<?> INSTANCE = new Skip<>();
    }
}
