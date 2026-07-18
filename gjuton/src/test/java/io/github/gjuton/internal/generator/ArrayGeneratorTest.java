package io.github.gjuton.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.ArraySchema;
import io.github.gjuton.internal.parser.SchemaParser;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

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

    @Test
    void containsForcesNonEmptyArrayEvenWithoutMinItems() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": {"type": "string"},
                    "contains": {"const": "x"}
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> {
            assertThat(arr).isNotEmpty();
            assertThat(arr).contains("x");
        });
    }

    @Test
    void containsRespectsMaxItems() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": {"type": "string"},
                    "contains": {"const": "x"},
                    "maxItems": 2
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> {
            assertThat(arr).hasSizeLessThanOrEqualTo(2);
            assertThat(arr).contains("x");
        });
    }

    @Test
    void containsEnsuresMatchingElementIsPresent() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": {"type": "string"},
                    "contains": {"const": "required-value"},
                    "minItems": 3
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr ->
                assertThat(arr).contains("required-value"));
    }

    @Test
    void draft7TupleProducesCorrectlyTypedPositionalElements() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": [
                        {"type": "string"},
                        {"type": "integer"}
                    ],
                    "minItems": 2
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> {
            assertThat(arr).hasSizeGreaterThanOrEqualTo(2);
            assertThat(arr.get(0)).isInstanceOf(String.class);
            assertThat(arr.get(1)).isInstanceOf(Number.class);
        });
    }

    @Test
    void containsDoesNotOverrideTuplePositions() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": [
                        {"type": "string"},
                        {"type": "integer"}
                    ],
                    "contains": {"const": true},
                    "minItems": 3
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> {
            assertThat(arr).hasSizeGreaterThanOrEqualTo(3);
            assertThat(arr.get(0)).isInstanceOf(String.class);
            assertThat(arr.get(1)).isInstanceOf(Number.class);
            assertThat(arr).contains(true);
        });
    }

    @Test
    void prefixItemsProducesCorrectlyTypedPositionalElements() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "prefixItems": [
                        {"type": "string"},
                        {"type": "integer"},
                        {"type": "boolean"}
                    ],
                    "minItems": 3
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> {
            assertThat(arr).hasSizeGreaterThanOrEqualTo(3);
            assertThat(arr.get(0)).isInstanceOf(String.class);
            assertThat(arr.get(1)).isInstanceOf(Number.class);
            assertThat(arr.get(2)).isInstanceOf(Boolean.class);
        });
    }

    @Test
    void additionalItemsFalseCapsArrayAtTupleSize() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": [
                        {"type": "string"},
                        {"type": "integer"}
                    ],
                    "additionalItems": false,
                    "minItems": 2
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> {
            assertThat(arr).hasSize(2);
            assertThat(arr.get(0)).isInstanceOf(String.class);
            assertThat(arr.get(1)).isInstanceOf(Number.class);
        });
    }

    @Test
    void prefixItemsWithItemsFalseCapsArrayAtTupleSize() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "prefixItems": [
                        {"type": "string"},
                        {"type": "integer"}
                    ],
                    "items": false,
                    "minItems": 2
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> {
            assertThat(arr).hasSize(2);
            assertThat(arr.get(0)).isInstanceOf(String.class);
            assertThat(arr.get(1)).isInstanceOf(Number.class);
        });
    }

    @Test
    void additionalItemsSchemaGeneratesTypedExtraElements() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": [
                        {"type": "string"}
                    ],
                    "additionalItems": {"type": "boolean"},
                    "minItems": 3
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> {
            assertThat(arr).hasSizeGreaterThanOrEqualTo(3);
            assertThat(arr.get(0)).isInstanceOf(String.class);
            for (int i = 1; i < arr.size(); i++) {
                assertThat(arr.get(i)).isInstanceOf(Boolean.class);
            }
        });
    }

    @Test
    void minItemsLargerThanTupleForcesGenerationPastPrefix() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "prefixItems": [
                        {"type": "string"},
                        {"type": "integer"}
                    ],
                    "items": {"type": "boolean"},
                    "minItems": 4
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> {
            assertThat(arr).hasSizeGreaterThanOrEqualTo(4);
            assertThat(arr.get(0)).isInstanceOf(String.class);
            assertThat(arr.get(1)).isInstanceOf(Number.class);
            for (int i = 2; i < arr.size(); i++) {
                assertThat(arr.get(i)).isInstanceOf(Boolean.class);
            }
        });
    }

    @Test
    void uniqueItemsProducesNoDuplicateElements() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": {"type": "integer", "minimum": 0, "maximum": 1000},
                    "uniqueItems": true,
                    "minItems": 5,
                    "maxItems": 5
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> assertThat(arr).doesNotHaveDuplicates());
    }

    @Test
    void uniqueItemsWithSmallEnumStillProducesAllDistinctElements() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": {"type": "string", "enum": ["a", "b", "c"]},
                    "uniqueItems": true,
                    "minItems": 3,
                    "maxItems": 3
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> {
            assertThat(arr).doesNotHaveDuplicates();
            assertThat(arr).hasSize(3);
        });
    }

    @Test
    void uniqueItemsThrowsWhenMinItemsExceedsValueSpace() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": {"type": "string", "enum": ["a", "b", "c"]},
                    "uniqueItems": true,
                    "minItems": 4
                }
                """);

        // when / then
        assertThatThrownBy(generator::generate).isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void uniqueItemsAppliesAcrossPrefixAndAdditionalPositions() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "prefixItems": [
                        {"type": "string", "enum": ["a", "b", "c"]},
                        {"type": "string", "enum": ["a", "b", "c"]}
                    ],
                    "items": {"type": "string", "enum": ["a", "b", "c"]},
                    "uniqueItems": true,
                    "minItems": 3,
                    "maxItems": 3
                }
                """);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> {
            assertThat(arr).doesNotHaveDuplicates();
            assertThat(arr).hasSize(3);
        });
    }

    @Test
    void uniqueItemsThrowsWhenPrefixItemsForceADuplicateConst() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "prefixItems": [
                        {"const": "x"},
                        {"const": "x"}
                    ],
                    "uniqueItems": true,
                    "minItems": 2
                }
                """);

        // when / then
        assertThatThrownBy(generator::generate).isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void requiredPropertyMissingFromItemPropertiesIsGeneratedInEveryElement() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "id": {"type": "integer"}
                        },
                        "required": ["id", "label"]
                    },
                    "minItems": 2,
                    "maxItems": 4
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(arr -> assertThat(arr).allSatisfy(
                item -> assertThat((Map<String, Object>) item).containsKey("label")));
    }

    @Test
    void containsForcingMinLengthAboveMaxItemsThrows() {
        var generator = arrayGenerator("""
                {
                    "type": "array",
                    "minItems": 0,
                    "maxItems": 0,
                    "items": {"type": "integer"},
                    "contains": {"const": 42}
                }
                """);

        // when / then
        assertThatThrownBy(generator::generate).isInstanceOf(UnsatisfiableSchemaException.class);
    }

    private static ArrayGenerator arrayGenerator(String json) {
        var document = SchemaParser.parse(json);
        return new ArrayGenerator(new GeneratorContext(document, new Random(42)), (ArraySchema) document.getRoot());
    }
}
