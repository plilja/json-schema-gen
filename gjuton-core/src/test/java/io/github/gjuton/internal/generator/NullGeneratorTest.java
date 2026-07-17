package io.github.gjuton.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NullGeneratorTest {

    @Test
    void alwaysReturnsNull() {
        var generator = new NullGenerator();

        // when
        var values = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(values).containsOnly((Object) null);
    }

    @Test
    void singleDeliberateValueEmittedAfterFirstCall() {
        // when
        var generator = new NullGenerator();

        // then
        assertThat(generator.totalCount()).isEqualTo(1);
        assertThat(generator.emittedCount()).isEqualTo(0);

        // when
        generator.generate();

        // then
        assertThat(generator.emittedCount()).isEqualTo(1);
    }
}
