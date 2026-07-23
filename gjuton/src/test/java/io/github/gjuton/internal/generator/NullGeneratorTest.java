package io.github.gjuton.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NullGeneratorTest {

    @Test
    void alwaysReturnsNull() {
        var context = TestContexts.withSeed(1);
        var generator = new NullGenerator(context);

        // when
        var values = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(values).containsOnly((Object) null);
    }

    @Test
    void firstCallRegistersAsNovel() {
        var context = TestContexts.withSeed(1);
        var generator = new NullGenerator(context);

        // when
        context.startRun();
        generator.generate();
        context.completeRun();

        // then
        assertThat(context.noveltyScore()).isEqualTo(1.0);
    }

    @Test
    void secondCallIsNotNovel() {
        var context = TestContexts.withSeed(1);
        var generator = new NullGenerator(context);

        // when
        context.startRun();
        generator.generate();
        context.completeRun();
        context.startRun();
        generator.generate();
        context.completeRun();

        // then
        assertThat(context.noveltyScore()).isEqualTo(0.5);
    }
}
