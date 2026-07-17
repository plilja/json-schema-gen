package io.github.gjuton.internal.generator;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BooleanGeneratorTest {

    @Test
    void firstCallProducesFalse() {
        var generator = new BooleanGenerator(withSeed(42));

        // when
        boolean result = generator.generate();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void secondCallProducesTrue() {
        var generator = new BooleanGenerator(withSeed(42));
        generator.generate();

        // when
        boolean result = generator.generate();

        // then
        assertThat(result).isTrue();
    }

    @Test
    void repeatedCallsProduceBothValues() {
        var generator = new BooleanGenerator(withSeed(42));

        // when
        var values = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(values).contains(true, false);
    }

    @Test
    void coversBothBooleansThenOneRandomValue() {
        // when
        var generator = new BooleanGenerator(withSeed(42));

        // then
        assertThat(generator.totalCount()).isEqualTo(3);
        assertThat(generator.emittedCount()).isEqualTo(0);

        // when: both boundary values, not yet complete
        generator.generate();
        generator.generate();

        // then
        assertThat(generator.emittedCount()).isEqualTo(2);

        // when: one random value completes the set
        generator.generate();

        // then
        assertThat(generator.emittedCount()).isEqualTo(3);
    }
}
