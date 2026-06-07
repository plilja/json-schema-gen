package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static se.plilja.jsonschemagen.internal.generator.TestContexts.withSeed;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnumGeneratorTest {

    @Test
    void exhaustsAllValuesInOrder() {
        var generator = new EnumGenerator(withSeed(42),List.of("red", "green", "blue"));

        // when
        var first = generator.generate();
        var second = generator.generate();
        var third = generator.generate();

        // then
        assertThat(first).isEqualTo("red");
        assertThat(second).isEqualTo("green");
        assertThat(third).isEqualTo("blue");
    }

    @Test
    void exhaustsMixedTypeValuesInOrder() {
        var generator = new EnumGenerator(withSeed(42),Arrays.asList("admin", 1, true, null));

        // when
        var first = generator.generate();
        var second = generator.generate();
        var third = generator.generate();
        var fourth = generator.generate();

        // then
        assertThat(first).isEqualTo("admin");
        assertThat(second).isEqualTo(1);
        assertThat(third).isEqualTo(true);
        assertThat(fourth).isNull();
    }

    @Test
    void producesRandomValuesAfterExhausting() {
        var generator = new EnumGenerator(withSeed(42),List.of("red", "green", "blue"));
        // exhaust all values
        generator.generate();
        generator.generate();
        generator.generate();

        // when
        var fourth = generator.generate();
        var fifth = generator.generate();
        var sixth = generator.generate();

        // then
        assertThat(fourth).isEqualTo("blue");
        assertThat(fifth).isEqualTo("red");
        assertThat(sixth).isEqualTo("red");
    }
}
