package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.List;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.util.RandomUtil;

/**
 * Generator for schemas with an {@code anyOf} keyword. Generates values
 * satisfying at least one branch, varying which and how many branches
 * are satisfied across successive calls.
 */
final class AnyOfGenerator extends PhaseGenerator<AnyOfGenerator.GenerationPhase, Object> {

    private final List<Schema> subSchemas;
    private int index = 0;

    enum GenerationPhase {
        EXHAUSTIVE_SINGLE, RANDOM_SUBSET
    }

    AnyOfGenerator(GeneratorContext context, Schema parent) {
        super(GenerationPhase.class, context);
        var parentCore = parent.toBuilder().anyOf(null).build();
        this.subSchemas = SchemaMerger.mergeEachWith(parent.getAnyOf(), parentCore);
        if (subSchemas.isEmpty()) {
            throw new UnsatisfiableSchemaException(
                    "anyOf has no branch compatible with the parent schema");
        }
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.EXHAUSTIVE_SINGLE;
    }

    @Override
    protected GenerationPhase advanceToNext(GenerationPhase current) {
        if (current == GenerationPhase.EXHAUSTIVE_SINGLE) {
            index++;
            if (index < subSchemas.size()) {
                return GenerationPhase.EXHAUSTIVE_SINGLE;
            }
        }
        return super.advanceToNext(current);
    }

    @Override
    protected GenerationResult<Object> generatePhase(GenerationPhase phase) {
        var pick = switch (phase) {
            case EXHAUSTIVE_SINGLE -> subSchemas.get(index);
            case RANDOM_SUBSET -> mergeRandomSubset();
        };
        return result(context.generatorFor(pick).generate());
    }

    private Schema mergeRandomSubset() {
        // Draw N in [1, len]; pick N branches at random and merge. On
        // UnsatisfiableSchemaException, decrement N and re-draw. N=1 is always
        // satisfiable because every branch in subSchemas was already proven
        // mergeable with the parent.
        for (int n = context.random().nextInt(subSchemas.size()) + 1; n >= 1; n--) {
            var subset = RandomUtil.randomSubset(subSchemas, n, context.random());
            try {
                return SchemaMerger.merge(subset);
            } catch (UnsatisfiableSchemaException ignored) {
                // TODO look into better solution that avoids using exceptions for control flow
                // try smaller
            }
        }
        throw new IllegalStateException("Unreachable: N=1 must succeed");
    }
}
