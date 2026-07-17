package io.github.gjuton.internal.generator;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.util.EnumUtil;

/**
 * Generator that walks through a sequence of named phases, producing
 * one value per {@link #generate()} call. Phases are tried in order;
 * a phase that cannot produce a value is skipped in favour of the next.
 */
public abstract class PhaseGenerator<E extends Enum<E>, R> implements Generator<R> {

    private static final int RETRY_BUDGET = 10;

    protected final GeneratorContext context;
    private final int phaseCount;
    private E phase;
    private long emitted;

    protected PhaseGenerator(Class<E> phaseClass, GeneratorContext context) {
        this.context = context;
        this.phaseCount = phaseClass.getEnumConstants().length;
        this.phase = EnumUtil.first(phaseClass);
    }

    public R generate() {
        // Minimal and random-only modes both start from a fixed phase instead of
        // the shared phase field, and don't persist advances to it: they retry
        // locally (a candidate may skip, e.g. on post-generation validation
        // failure) rather than requiring first-attempt success, and the shared
        // cycling state belongs to the exhaustive cycle across separate
        // generate() calls. Minimal mode takes precedence so recursion still
        // terminates even when random-only is configured.
        UnsatisfiableSchemaException lastException = null;
        boolean cycling = !context.isMinimal() && !context.isRandomOnly();
        var candidate = startingPhase();
        for (int attempt = 0; attempt < RETRY_BUDGET; attempt++) {
            GenerationResult<R> result;
            try {
                result = generatePhase(candidate);
            } catch (UnsatisfiableSchemaException e) {
                lastException = e;
                result = GenerationResult.skip();
            }
            candidate = advanceToNext(candidate);
            if (cycling) {
                phase = candidate;
            }
            if (result instanceof GenerationResult.Present<R> present) {
                if (cycling) {
                    emitted = Math.min(emitted + 1, phaseCount);
                }
                return present.value();
            }
        }
        throw lastException != null ? lastException
                : new UnsatisfiableSchemaException("Unable to generate a value satisfying the schema");
    }

    private E startingPhase() {
        if (context.isMinimal()) {
            return minimalPhase();
        }
        if (context.isRandomOnly()) {
            return randomPhase();
        }
        return phase;
    }

    protected E advanceToNext(E current) {
        return EnumUtil.next(current);
    }

    /**
     * The generator's deliberate values are its boundary phases plus one slot
     * for the terminal random phase, which every generator must emit once to be
     * complete.
     */
    @Override
    public long totalCount() {
        return phaseCount;
    }

    @Override
    public long emittedCount() {
        return emitted;
    }

    /**
     * The ordinal, within the declared phase order, of the phase this generator
     * is currently positioned at. A caller compares it against a particular
     * phase's ordinal to tell whether generation has reached that phase.
     */
    protected final int currentPhaseOrdinal() {
        return phase.ordinal();
    }

    /**
     * Returns the phase tried first in minimal mode. May skip — unlike the
     * normal phase cycle, it is not required to always produce a value.
     */
    protected abstract E minimalPhase();

    /**
     * Returns the phase that emits a purely random value, used exclusively in
     * random-only mode. By convention this is the last declared phase, which
     * every generator uses as the terminal fallback of its boundary-value cycle.
     */
    protected E randomPhase() {
        return EnumUtil.last(phase.getDeclaringClass());
    }

    protected abstract GenerationResult<R> generatePhase(E phase);
}
