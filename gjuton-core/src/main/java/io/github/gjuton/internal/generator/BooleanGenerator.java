package io.github.gjuton.internal.generator;

import static io.github.gjuton.internal.generator.GenerationResult.result;

final class BooleanGenerator extends PhaseGenerator<BooleanGenerator.GenerationPhase, Boolean> {

    enum GenerationPhase {
        FALSE, TRUE, RANDOM
    }

    BooleanGenerator(GeneratorContext context) {
        super(GenerationPhase.class, context);
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.FALSE;
    }

    @Override
    protected GenerationResult<Boolean> generatePhase(GenerationPhase phase) {
        boolean value = switch (phase) {
            case FALSE -> false;
            case TRUE -> true;
            case RANDOM -> context.random().nextBoolean();
        };
        return result(value);
    }
}
