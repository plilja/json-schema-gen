package se.plilja.jsonschemagen.internal.generator;

import se.plilja.jsonschemagen.internal.util.EnumUtil;

/**
 * Generator that walks through a sequence of named phases.
 *
 * <p>Each call to {@link #generate()} tries the current phase via
 * {@link #generatePhase}. If the phase returns {@link GenerationResult.Skip},
 * the generator advances to the next phase and retries until a phase
 * produces a value.
 */
public abstract class PhaseGenerator<E extends Enum<E>, R> implements Generator<R> {

    protected final GeneratorContext context;
    private E phase;

    protected PhaseGenerator(Class<E> phaseClass, GeneratorContext context) {
        this.context = context;
        this.phase = EnumUtil.first(phaseClass);
    }

    public R generate() {
        if (context.isMinimal()) {
            var result = generatePhase(minimalPhase());
            if (result instanceof GenerationResult.Present<R> present) {
                return present.value();
            }
            throw new IllegalStateException("Minimal phase must always produce a value");
        }
        while (true) {
            var result = generatePhase(phase);
            var prev = phase;
            phase = advanceToNext(phase);
            if (result instanceof GenerationResult.Present<R> present) {
                return present.value();
            }
            if (prev == phase) {
                // We reached the end of the phases but were unable to generate any value
                throw new IllegalStateException("No applicable phase found");
            }
        }
    }

    protected E advanceToNext(E current) {
        return EnumUtil.next(current);
    }

    /**
     * Returns the phase that would generate the smallest possible result satisfying
     * the constraints. Please note that this phase should not be skippable.
     */
    protected abstract E minimalPhase();

    protected abstract GenerationResult<R> generatePhase(E phase);
}
