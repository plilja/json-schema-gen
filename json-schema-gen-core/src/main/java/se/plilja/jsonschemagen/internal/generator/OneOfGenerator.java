package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.List;
import se.plilja.jsonschemagen.internal.model.Schema;

final class OneOfGenerator extends PhaseGenerator<OneOfGenerator.GenerationPhase, Object> {

    private final List<Schema> subSchemas;
    private int index = 0;

    enum GenerationPhase {
        EXHAUSTIVE, RANDOM
    }

    OneOfGenerator(GeneratorContext context, List<Schema> subSchemas) {
        super(GenerationPhase.class, context);
        this.subSchemas = subSchemas;
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.EXHAUSTIVE;
    }

    @Override
    protected GenerationPhase advanceToNext(GenerationPhase current) {
        if (current == GenerationPhase.EXHAUSTIVE) {
            index++;
            if (index < subSchemas.size()) {
                return GenerationPhase.EXHAUSTIVE;
            }
        }
        return super.advanceToNext(current);
    }

    @Override
    protected GenerationResult<Object> generatePhase(GenerationPhase phase) {
        var pick = switch (phase) {
            case EXHAUSTIVE -> subSchemas.get(index);
            case RANDOM -> subSchemas.get(context.random().nextInt(subSchemas.size()));
        };
        return result(context.generatorFor(pick).generate());
    }
}
