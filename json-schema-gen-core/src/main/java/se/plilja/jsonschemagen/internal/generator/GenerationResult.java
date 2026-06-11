package se.plilja.jsonschemagen.internal.generator;

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
