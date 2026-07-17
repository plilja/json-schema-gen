package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import se.plilja.jsonschemagen.internal.util.EnumUtil;
import se.plilja.jsonschemagen.internal.util.RandomUtil;

/**
 * Generates values for schemas with no {@code type} keyword. Since any JSON
 * value is valid, phases cycle through representative values spanning all
 * JSON types, then pick randomly from the same pool.
 */
final class UntypedGenerator extends PhaseGenerator<UntypedGenerator.GenerationPhase, Object> {

    private static final List<Object> TYPE_SAMPLES = Collections.unmodifiableList(Arrays.asList(
            null,
            false,
            true,
            -1,
            0,
            1,
            "",
            "foo",
            List.of(),
            List.of("foo", 17),
            Map.of(),
            Map.of("a", 1),
            Map.of("a", 1, "b", List.of("baz", 12))
    ));

    private int cycleIndex = 0;

    enum GenerationPhase {
        CYCLE, RANDOM
    }

    UntypedGenerator(GeneratorContext context) {
        super(GenerationPhase.class, context);
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.CYCLE;
    }

    /**
     * The deliberate value set is the fixed collection of type-spanning
     * samples, so full coverage means each sample has been emitted at least
     * once.
     */
    @Override
    public long totalCount() {
        return TYPE_SAMPLES.size();
    }

    @Override
    public long emittedCount() {
        return Math.min(cycleIndex, TYPE_SAMPLES.size());
    }

    @Override
    protected GenerationPhase advanceToNext(GenerationPhase current) {
        if (current == GenerationPhase.CYCLE && cycleIndex < TYPE_SAMPLES.size()) {
            return GenerationPhase.CYCLE;
        }
        return EnumUtil.next(current);
    }

    @Override
    protected GenerationResult<Object> generatePhase(GenerationPhase phase) {
        return switch (phase) {
            case CYCLE -> result(TYPE_SAMPLES.get(cycleIndex++));
            case RANDOM -> result(RandomUtil.randomOne(TYPE_SAMPLES, context.random()));
        };
    }
}
