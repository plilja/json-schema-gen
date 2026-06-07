package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.internal.model.ArraySchema;

class ArrayGeneratorTest {

    @Test
    void minItemsIsAlwaysRespected() {
        var schema = TestParser.parse("""
                {
                    "type": "array",
                    "items": {"type": "string"},
                    "minItems": 3
                }
                """, ArraySchema.class);
        var generator = new ArrayGenerator(new Random(42), schema);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> assertThat(arr).hasSizeGreaterThanOrEqualTo(3));
    }

    @Test
    void maxItemsIsAlwaysRespected() {
        var schema = TestParser.parse("""
                {
                    "type": "array",
                    "items": {"type": "string"},
                    "minItems": 1,
                    "maxItems": 4
                }
                """, ArraySchema.class);
        var generator = new ArrayGenerator(new Random(42), schema);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> assertThat(arr).hasSizeLessThanOrEqualTo(4));
    }

    @Test
    void boundaryLengthsAreCoveredAcrossRepeatedCalls() {
        var schema = TestParser.parse("""
                {
                    "type": "array",
                    "items": {"type": "string"},
                    "minItems": 2,
                    "maxItems": 5
                }
                """, ArraySchema.class);
        var generator = new ArrayGenerator(new Random(42), schema);

        // when
        var lengths = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .map(List::size)
                .toList();

        // then
        assertThat(lengths).contains(2);
        assertThat(lengths).contains(5);
    }

    @Test
    void emptyArrayIsCoveredWhenMinItemsIsZero() {
        var schema = TestParser.parse("""
                {
                    "type": "array",
                    "items": {"type": "string"},
                    "maxItems": 5
                }
                """, ArraySchema.class);
        var generator = new ArrayGenerator(new Random(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).anyMatch(List::isEmpty);
    }

    @Test
    void elementsConformToItemsSubSchema() {
        var schema = TestParser.parse("""
                {
                    "type": "array",
                    "items": {"type": "string"},
                    "minItems": 2
                }
                """, ArraySchema.class);
        var generator = new ArrayGenerator(new Random(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> assertThat(arr).isNotEmpty());
        assertThat(results).allSatisfy(arr -> assertThat(arr).allMatch(e -> e instanceof String));
    }
}
