package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.internal.model.ArraySchema;
import se.plilja.jsonschemagen.internal.parser.SchemaParser;

class ArrayGeneratorTest {

    @Test
    void minItemsIsAlwaysRespected() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": {"type": "string"},
                    "minItems": 3
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> assertThat(arr).hasSizeGreaterThanOrEqualTo(3));
    }

    @Test
    void maxItemsIsAlwaysRespected() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": {"type": "string"},
                    "minItems": 1,
                    "maxItems": 4
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> assertThat(arr).hasSizeLessThanOrEqualTo(4));
    }

    @Test
    void boundaryLengthsAreCoveredAcrossRepeatedCalls() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": {"type": "string"},
                    "minItems": 2,
                    "maxItems": 5
                }
                """);

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
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": {"type": "string"},
                    "maxItems": 5
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).anyMatch(List::isEmpty);
    }

    @Test
    void elementsConformToItemsSubSchema() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": {"type": "string"},
                    "minItems": 2
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> assertThat(arr).isNotEmpty());
        assertThat(results).allSatisfy(arr -> assertThat(arr).allMatch(e -> e instanceof String));
    }

    private static ArrayGenerator arrayGenerator(String json) {
        var document = SchemaParser.parse(json);
        return new ArrayGenerator(new GeneratorContext(document, new Random(42)), (ArraySchema) document.getRoot());
    }
}
