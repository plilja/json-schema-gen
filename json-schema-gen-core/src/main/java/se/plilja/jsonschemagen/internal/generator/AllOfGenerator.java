package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.ArrayList;
import se.plilja.jsonschemagen.internal.model.Schema;

final class AllOfGenerator extends PhaseGenerator<AllOfGenerator.GenerationPhase, Object> {

    private final Schema merged;

    enum GenerationPhase {
        ONLY_PHASE
    }

    AllOfGenerator(GeneratorContext context, Schema parent) {
        super(GenerationPhase.class, context);
        if (parent.getAllOf().isEmpty()) {
            throw new IllegalArgumentException("allOf must contain at least one sub-schema");
        }
        var branches = new ArrayList<>(parent.getAllOf());
        branches.add(parent.toBuilder().allOf(null).build());
        this.merged = SchemaMerger.merge(branches);
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.ONLY_PHASE;
    }

    @Override
    protected GenerationResult<Object> generatePhase(GenerationPhase phase) {
        return result(context.generatorFor(merged).generate());
    }
}
