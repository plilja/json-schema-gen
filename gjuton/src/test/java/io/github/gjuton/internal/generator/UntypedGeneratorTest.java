package io.github.gjuton.internal.generator;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UntypedGeneratorTest {

    @Test
    void firstCallReturnsNull() {
        var generator = new UntypedGenerator(withSeed(42));

        // when
        var result = generator.generate();

        // then
        assertThat(result).isNull();
    }

    @Test
    void successiveCallsCycleThroughJsonTypesThenPickRandomly() {
        var generator = new UntypedGenerator(withSeed(42));

        // when
        var values = IntStream.range(0, 18)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(values).containsExactly(
                // CYCLE phase — walks through TYPE_SAMPLES
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
                Map.of("a", 1, "b", List.of("baz", 12)),
                // RANDOM phase
                null,
                "foo",
                List.of("foo", 17),
                Map.of("a", 1, "b", List.of("baz", 12)),
                null
        );
    }

}
