package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

final class NullGenerator extends PhaseGenerator<NullGenerator.GenerationPhase, Object> {

    enum GenerationPhase {
        NULL
    }

    NullGenerator(GeneratorContext context) {
        super(GenerationPhase.class, context);
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.NULL;
    }

    @Override
    protected GenerationResult<Object> generatePhase(GenerationPhase phase) {
        return result(null);
    }
}
