package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static se.plilja.jsonschemagen.internal.generator.TestContexts.withSeed;

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

    @Test
    void totalIsSampleCountAndEmittedTracksCycle() {
        // when
        var generator = new UntypedGenerator(withSeed(42));

        // then
        assertThat(generator.totalCount()).isEqualTo(13);
        assertThat(generator.emittedCount()).isEqualTo(0);

        // when: walk the full type-spanning cycle
        for (int i = 0; i < 13; i++) {
            generator.generate();
        }

        // then
        assertThat(generator.emittedCount()).isEqualTo(13);

        // when: random phase re-picks without exceeding the deliberate set
        generator.generate();

        // then
        assertThat(generator.emittedCount()).isEqualTo(13);
    }
}
