package se.plilja.jsonschemagen.internal.generator;

import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.util.EnumUtil;

/**
 * Generator that walks through a sequence of named phases, producing
 * one value per {@link #generate()} call. Phases are tried in order;
 * a phase that cannot produce a value is skipped in favour of the next.
 */
public abstract class PhaseGenerator<E extends Enum<E>, R> implements Generator<R> {

    private static final int RETRY_BUDGET = 10;

    protected final GeneratorContext context;
    private E phase;

    protected PhaseGenerator(Class<E> phaseClass, GeneratorContext context) {
        this.context = context;
        this.phase = EnumUtil.first(phaseClass);
    }

    public R generate() {
        // In minimal mode, start from minimalPhase() instead of the shared phase
        // field, and don't persist advances to it — minimal mode may skip (e.g. a
        // candidate failing post-generation validation), so it retries locally
        // rather than requiring first-attempt success, and the shared cycling
        // state belongs to the non-minimal cycle across separate generate() calls.
        UnsatisfiableSchemaException lastException = null;
        var candidate = context.isMinimal() ? minimalPhase() : phase;
        for (int attempt = 0; attempt < RETRY_BUDGET; attempt++) {
            GenerationResult<R> result;
            try {
                result = generatePhase(candidate);
            } catch (UnsatisfiableSchemaException e) {
                lastException = e;
                result = GenerationResult.skip();
            }
            candidate = advanceToNext(candidate);
            if (!context.isMinimal()) {
                phase = candidate;
            }
            if (result instanceof GenerationResult.Present<R> present) {
                return present.value();
            }
        }
        throw lastException != null ? lastException
                : new UnsatisfiableSchemaException("Unable to generate a value satisfying the schema");
    }

    protected E advanceToNext(E current) {
        return EnumUtil.next(current);
    }

    /**
     * Returns the phase tried first in minimal mode. May skip — unlike the
     * normal phase cycle, it is not required to always produce a value.
     */
    protected abstract E minimalPhase();

    protected abstract GenerationResult<R> generatePhase(E phase);
}
