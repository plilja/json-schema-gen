package io.github.gjuton.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhaseGeneratorTest {

    @Test
    void firstCallRegistersAsNovel() {
        var context = TestContexts.withSeed(1);
        var generator = new BooleanGenerator(context);

        // when
        context.startRun();
        generator.generate();
        context.completeRun();

        // then
        assertThat(context.noveltyScore()).isEqualTo(1.0);
    }

    @Test
    void repeatingTheTerminalRandomPhaseStopsContributingNovelty() {
        var context = TestContexts.withSeed(1);
        var generator = new BooleanGenerator(context);

        // when
        // three runs cycle FALSE, TRUE, RANDOM — each a novel phase
        for (int i = 0; i < 3; i++) {
            context.startRun();
            generator.generate();
            context.completeRun();
        }
        // phase caps at RANDOM (no wraparound), so a fourth run repeats it
        context.startRun();
        generator.generate();
        context.completeRun();

        // then
        assertThat(context.noveltyScore()).isEqualTo(0.75);
    }

    @Test
    void unexpectedExceptionFromGeneratePhaseStillRollsBackVisitsItCaused() {
        var context = TestContexts.withSeed(1);
        var generator = new ThrowingGenerator(context);

        // when
        context.startRun();
        assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
        context.completeRun();

        // then
        assertThat(context.noveltyScore()).isEqualTo(0.0);
    }

    /**
     * Registers a visit on a nested generator and then throws an exception the
     * retry loop doesn't recognize, to prove that visit is rolled back rather
     * than left to be committed by {@link GeneratorContext#completeRun}.
     */
    private static final class ThrowingGenerator extends PhaseGenerator<ThrowingGenerator.Phase, Object> {

        enum Phase {
            ONLY
        }

        private final BooleanGenerator inner;

        ThrowingGenerator(GeneratorContext context) {
            super(Phase.class, context);
            this.inner = new BooleanGenerator(context);
        }

        @Override
        protected Phase minimalPhase() {
            return Phase.ONLY;
        }

        @Override
        protected GenerationResult<Object> generatePhase(Phase phase) {
            inner.generate();
            throw new IllegalStateException("unexpected failure");
        }
    }
}
