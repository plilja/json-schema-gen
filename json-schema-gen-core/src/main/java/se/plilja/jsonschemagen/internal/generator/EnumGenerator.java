package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.List;

/**
 * Generator for schemas with an {@code enum} keyword. An {@code enum}
 * restricts the value to a fixed set of allowed literals.
 */
final class EnumGenerator extends PhaseGenerator<EnumGenerator.GenerationPhase, Object> {

    private final List<Object> values;
    private int index = 0;

    enum GenerationPhase {
        EXHAUSTIVE, RANDOM
    }

    EnumGenerator(GeneratorContext context, List<Object> values) {
        super(GenerationPhase.class, context);
        this.values = values;
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.EXHAUSTIVE;
    }

    @Override
    protected GenerationPhase advanceToNext(GenerationPhase current) {
        if (current == GenerationPhase.EXHAUSTIVE) {
            index++;
            if (index < values.size()) {
                return GenerationPhase.EXHAUSTIVE;
            }
        }
        return super.advanceToNext(current);
    }

    @Override
    protected GenerationResult<Object> generatePhase(GenerationPhase phase) {
        var value = switch (phase) {
            case EXHAUSTIVE -> values.get(index);
            case RANDOM -> values.get(context.random().nextInt(values.size()));
        };
        return result(value);
    }
}
