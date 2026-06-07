package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static se.plilja.jsonschemagen.internal.generator.TestContexts.withSeed;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.internal.model.BooleanSchema;

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
}
