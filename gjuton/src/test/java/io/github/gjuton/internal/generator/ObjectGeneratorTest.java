package io.github.gjuton.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.ObjectSchema;
import io.github.gjuton.internal.model.Schema;
import io.github.gjuton.internal.parser.SchemaParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ObjectGeneratorTest {

    @Test
    void generatesObjectWithRequiredStringField() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"}
                    },
                    "required": ["name"]
                }
                """);

        // when
        var result = generator.generate();

        // then
        assertThat(result).containsKey("name");
        assertThat(result.get("name")).isInstanceOf(String.class);
    }

    @Test
    void requiredFieldsAlwaysPresent() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "nickname": {"type": "string"}
                    },
                    "required": ["name"]
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).containsKey("name"));
    }

    @Test
    void optionalFieldsAppearBothPresentAndAbsent() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "nickname": {"type": "string"}
                    },
                    "required": ["name"]
                }
                """);

        // when
        var results = IntStream.range(0, 2)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).anyMatch(obj -> obj.containsKey("nickname"));
        assertThat(results).anyMatch(obj -> !obj.containsKey("nickname"));
    }

    @Test
    void emptyPropertiesGeneratesEmptyObject() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {}
                }
                """);

        // when
        var result = generator.generate();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void absentPropertiesAndRequiredGeneratesEmptyObject() {
        var generator = objectGenerator("""
                {"type": "object"}
                """);

        // when
        var result = generator.generate();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void nestedObjectsAreGeneratedRecursively() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "address": {
                            "type": "object",
                            "properties": {
                                "street": {"type": "string"}
                            },
                            "required": ["street"]
                        }
                    },
                    "required": ["address"]
                }
                """);

        // when
        var result = generator.generate();

        // then
        assertThat(result).containsKey("address");
        var address = (Map<String, Object>) result.get("address");
        assertThat(address).containsKey("street");
        assertThat(address.get("street")).isInstanceOf(String.class);
    }

    @Test
    void additionalPropertiesFalseStillEmitsDeclaredOptionalFields() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "nickname": {"type": "string"}
                    },
                    "required": ["name"],
                    "additionalProperties": false
                }
                """);

        // when
        var results = IntStream.range(0, 2)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).anyMatch(obj -> obj.containsKey("nickname"));
    }

    @Test
    void minPropertiesWithRequiredFieldsCountsRequiredTowardMinimum() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "id": {"type": "integer"},
                        "name": {"type": "string"},
                        "nickname": {"type": "string"}
                    },
                    "required": ["id"],
                    "minProperties": 2
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> {
            assertThat(obj).hasSizeGreaterThanOrEqualTo(2);
            assertThat(obj).containsKey("id");
        });
    }

    @Test
    void minPropertiesForcesOptionalFieldsToAppear() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "string"},
                        "b": {"type": "string"},
                        "c": {"type": "string"}
                    },
                    "minProperties": 2
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).hasSizeGreaterThanOrEqualTo(2));
    }

    @Test
    void requiredFalseSchemaPropertyThrows() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "impossible": false
                    },
                    "required": ["name", "impossible"]
                }
                """);

        // when / then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void minPropertiesSkipsUnsatisfiableWhenPadding() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "string"},
                        "b": {"type": "string"},
                        "c": {"type": "string"},
                        "d": {"type": "string"},
                        "forbidden": false
                    },
                    "minProperties": 4
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> {
            assertThat(obj).hasSizeGreaterThanOrEqualTo(4);
            assertThat(obj).doesNotContainKey("forbidden");
        });
    }

    @Test
    void optionalFalseSchemaPropertyIsNeverIncluded() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "forbidden": false
                    },
                    "required": ["name"]
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).doesNotContainKey("forbidden"));
    }

    @Test
    void boundaryValuesExercisedAcrossIterations() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "string"},
                        "b": {"type": "string"},
                        "c": {"type": "string"},
                        "d": {"type": "string"},
                        "e": {"type": "string"}
                    },
                    "minProperties": 2,
                    "maxProperties": 4
                }
                """);

        // when
        var sizes = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .map(Map::size)
                .toList();

        // then
        assertThat(sizes).contains(2);
        assertThat(sizes).contains(4);
    }

    @Test
    void minAndMaxPropertiesEnforcedTogether() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "string"},
                        "b": {"type": "string"},
                        "c": {"type": "string"},
                        "d": {"type": "string"},
                        "e": {"type": "string"}
                    },
                    "minProperties": 2,
                    "maxProperties": 4
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> {
            assertThat(obj).hasSizeGreaterThanOrEqualTo(2);
            assertThat(obj).hasSizeLessThanOrEqualTo(4);
        });
    }

    @Test
    void maxPropertiesLessThanRequiredCountThrows() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "string"},
                        "b": {"type": "string"},
                        "c": {"type": "string"}
                    },
                    "required": ["a", "b", "c"],
                    "maxProperties": 2
                }
                """);

        // when / then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void maxPropertiesWithRequiredFieldsTrimsOptional() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "id": {"type": "integer"},
                        "name": {"type": "string"},
                        "nickname": {"type": "string"},
                        "email": {"type": "string"}
                    },
                    "required": ["id", "name"],
                    "maxProperties": 3
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> {
            assertThat(obj).containsKey("id");
            assertThat(obj).containsKey("name");
            assertThat(obj).hasSizeLessThanOrEqualTo(3);
        });
    }

    @Test
    void requiredDependentRequiredExceedingMaxPropertiesThrows() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "trigger": {"type": "string"},
                        "dep1": {"type": "string"},
                        "dep2": {"type": "string"}
                    },
                    "required": ["trigger"],
                    "dependentRequired": {
                        "trigger": ["dep1", "dep2"]
                    },
                    "maxProperties": 2
                }
                """);

        // when / then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void optionalDependentRequiredSkippedWhenExceedingMaxProperties() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "string"},
                        "trigger": {"type": "string"},
                        "dep1": {"type": "string"},
                        "dep2": {"type": "string"}
                    },
                    "dependentRequired": {
                        "trigger": ["dep1", "dep2"]
                    },
                    "maxProperties": 2
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).hasSizeLessThanOrEqualTo(2));
    }

    @Test
    void minPropertiesExceedsAvailablePropertiesThrows() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "string"},
                        "b": false
                    },
                    "additionalProperties": false,
                    "minProperties": 3
                }
                """);

        // when / then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void maxPropertiesCapsKeyCount() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "string"},
                        "b": {"type": "string"},
                        "c": {"type": "string"},
                        "d": {"type": "string"}
                    },
                    "maxProperties": 2
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).hasSizeLessThanOrEqualTo(2));
    }

    @Test
    void circularDependentRequiredBothPresentOrAbsent() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "string"},
                        "b": {"type": "string"},
                        "c": {"type": "string"}
                    },
                    "dependentRequired": {
                        "a": ["b"],
                        "b": ["a"]
                    }
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> {
            if (obj.containsKey("a")) {
                assertThat(obj).containsKey("b");
            }
            if (obj.containsKey("b")) {
                assertThat(obj).containsKey("a");
            }
        });
    }

    @Test
    void additionalPropertiesTrueWithMinPropertiesSynthesizesEntries() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "additionalProperties": true,
                    "minProperties": 2
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).hasSizeGreaterThanOrEqualTo(2));
    }

    @Test
    void absentAdditionalPropertiesWithMinPropertiesSynthesizesEntries() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "minProperties": 1
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).hasSizeGreaterThanOrEqualTo(1));
    }

    @Test
    void additionalPropertiesFalseWithMinPropertiesExceedingNamedThrows() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "additionalProperties": false,
                    "minProperties": 1
                }
                """);

        // when / then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void namedPropertiesTakePriorityOverSynthesized() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "id": {"type": "integer"}
                    },
                    "required": ["id"],
                    "additionalProperties": {"type": "string"},
                    "minProperties": 3
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> {
            assertThat(obj).hasSizeGreaterThanOrEqualTo(3);
            assertThat(obj).containsKey("id");
            assertThat(obj.get("id")).isInstanceOf(Number.class);
        });
    }

    @Test
    void additionalPropertiesSchemaWithMinPropertiesSynthesizesEntries() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "additionalProperties": {"type": "string"},
                    "minProperties": 2
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> {
            assertThat(obj).hasSizeGreaterThanOrEqualTo(2);
            obj.values().forEach(v -> assertThat(v).isInstanceOf(String.class));
        });
    }

    @Test
    void namedPropertyMatchingPatternGetsPatternSchemaMerged() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "foo": {"type": "string"}
                    },
                    "patternProperties": {
                        "^f": {"type": "string", "minLength": 3, "maxLength": 3}
                    },
                    "required": ["foo"]
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> assertThat((String) obj.get("foo")).hasSize(3));
    }

    @Test
    void propertyMatchingMultiplePatternsGetsAllSchemasMerged() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "foo": {"type": "string"}
                    },
                    "patternProperties": {
                        "^f": {"type": "string", "minLength": 2},
                        "oo$": {"type": "string", "maxLength": 4}
                    },
                    "required": ["foo"]
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> {
            var value = (String) obj.get("foo");
            assertThat(value.length()).isGreaterThanOrEqualTo(2).isLessThanOrEqualTo(4);
        });
    }

    @Test
    void unmatchedPatternDoesNotConstrainProperty() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "bar": {"type": "string"}
                    },
                    "patternProperties": {
                        "^f": {"maxLength": 1}
                    },
                    "required": ["bar"]
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).anySatisfy(obj -> assertThat(((String) obj.get("bar")).length()).isGreaterThan(1));
    }

    @Test
    void additionalPropertiesFalseWithPatternPropertiesSynthesizesMatchingNames() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "additionalProperties": false,
                    "patternProperties": {
                        "^x[0-9]$": {"type": "string"}
                    },
                    "minProperties": 2
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> {
            assertThat(obj).hasSizeGreaterThanOrEqualTo(2);
            obj.keySet().forEach(key -> assertThat(key).matches("x[0-9]"));
        });
    }

    @Test
    void synthesizedPropertyMatchingPatternGetsPatternSchemaMerged() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "additionalProperties": true,
                    "patternProperties": {
                        "^prop": {"type": "string", "maxLength": 2}
                    },
                    "minProperties": 1
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> {
            assertThat(obj).hasSizeGreaterThanOrEqualTo(1);
            obj.forEach((key, value) -> assertThat((String) value).hasSizeLessThanOrEqualTo(2));
        });
    }

    @Test
    void requiredPropertyMissingFromPropertiesIsGeneratedFromAdditionalProperties() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "test": {"type": "string"}
                    },
                    "required": ["test", "dropped"]
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).containsKey("dropped"));
    }

    @Test
    void requiredPropertyMissingFromPropertiesIsGeneratedFromMatchingPatternProperties() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "test": {"type": "string"}
                    },
                    "patternProperties": {
                        "^extra": {"type": "integer", "minimum": 10, "maximum": 20}
                    },
                    "required": ["test", "extraValue"]
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> {
            assertThat(obj).containsKey("extraValue");
            assertThat((Long) obj.get("extraValue")).isBetween(10L, 20L);
        });
    }

    @Test
    void requiredPropertyMissingFromPropertiesWithAdditionalPropertiesFalseThrows() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "test": {"type": "string"}
                    },
                    "required": ["test", "dropped"],
                    "additionalProperties": false
                }
                """);

        // when / then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(UnsatisfiableSchemaException.class)
                .hasMessageContaining("dropped");
    }

    @Test
    void additionalPropertiesOptionSynthesizesExtraFields() {
        var generator = objectGeneratorWithAdditionalProperties("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "integer"}
                    },
                    "required": ["a"]
                }
                """);

        // when
        var results = generate(generator, 200);

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).containsKey("a"));
        assertThat(results).anySatisfy(obj -> assertThat(obj).hasSizeGreaterThan(1));
        // Never more than the one named property plus the synthesis headroom.
        assertThat(results).allSatisfy(obj -> assertThat(obj).hasSizeLessThanOrEqualTo(4));
    }

    @Test
    void additionalPropertiesOptionRespectsAdditionalPropertiesFalse() {
        var generator = objectGeneratorWithAdditionalProperties("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "integer"}
                    },
                    "required": ["a"],
                    "additionalProperties": false
                }
                """);

        // when
        var results = generate(generator, 200);

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).containsOnlyKeys("a"));
    }

    @Test
    void additionalPropertiesOptionRespectsMaxProperties() {
        var generator = objectGeneratorWithAdditionalProperties("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "integer"}
                    },
                    "required": ["a"],
                    "maxProperties": 2
                }
                """);

        // when
        var results = generate(generator, 200);

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).hasSizeLessThanOrEqualTo(2));
        assertThat(results).anySatisfy(obj -> assertThat(obj).hasSize(2));
    }

    @Test
    void withoutAdditionalPropertiesOptionNoExtraFieldsAreSynthesized() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "integer"}
                    },
                    "required": ["a"]
                }
                """);

        // when
        var results = generate(generator, 200);

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).containsOnlyKeys("a"));
    }

    @Test
    void dependentSchemaMergedPropertyEventuallyProducesANonEmptyValue() {
        // "toggle" is declared both in the base schema and in the schema
        // "trigger" pulls in via dependentSchemas, so resolving it merges the
        // two on every generate() call. If that merge isn't memoized, the
        // merged property schema is a fresh instance each time, so its own
        // generator never advances past its first boundary phase (the empty
        // string) and "toggle" would stay "" forever.
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "required": ["trigger", "toggle"],
                    "properties": {
                        "trigger": {"const": "x"},
                        "toggle": {"type": "string"}
                    },
                    "dependentSchemas": {
                        "trigger": {
                            "properties": {"toggle": {"type": "string"}}
                        }
                    }
                }
                """);

        // when
        var values = generate(generator, 10).stream().map(obj -> obj.get("toggle")).toList();

        // then
        assertThat(values).anyMatch(v -> !"".equals(v));
    }

    @Test
    void patternPropertyMergedPropertyEventuallyProducesANonEmptyValue() {
        // "toggle" is declared in properties and also matched by a
        // patternProperties regex, so resolveFieldSchema merges the two on
        // every generate() call — same identity-instability risk as the
        // dependentSchemas case above.
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "required": ["toggle"],
                    "properties": {"toggle": {"type": "string"}},
                    "patternProperties": {"^to.*": {"type": "string"}}
                }
                """);

        // when
        var values = generate(generator, 10).stream().map(obj -> obj.get("toggle")).toList();

        // then
        assertThat(values).anyMatch(v -> !"".equals(v));
    }

    private static List<Map<String, Object>> generate(ObjectGenerator generator, int iterations) {
        return IntStream.range(0, iterations).mapToObj(i -> generator.generate()).toList();
    }

    @Nested
    class ComputeImpliedProperties {

        @Test
        void returnsPropertyItselfWhenNoDependencies() {
            // when
            var result = ObjectGenerator.computeImpliedProperties("a", Map.of(), Map.of());

            // then
            assertThat(result).containsExactly("a");
        }

        @Test
        void includesDirectDependentRequired() {
            // when
            var result = ObjectGenerator.computeImpliedProperties("a",
                    Map.of("a", List.of("b", "c")),
                    Map.of());

            // then
            assertThat(result).containsExactlyInAnyOrder("a", "b", "c");
        }

        @Test
        void followsDependentRequiredTransitively() {
            // when
            var result = ObjectGenerator.computeImpliedProperties("a",
                    Map.of("a", List.of("b"), "b", List.of("c")),
                    Map.of());

            // then
            assertThat(result).containsExactlyInAnyOrder("a", "b", "c");
        }

        @Test
        void terminatesOnCircularDependentRequired() {
            // when
            var result = ObjectGenerator.computeImpliedProperties("a",
                    Map.of("a", List.of("b"), "b", List.of("a")),
                    Map.of());

            // then
            assertThat(result).containsExactlyInAnyOrder("a", "b");
        }

        @Test
        void includesRequiredFromDependentSchema() {
            var depSchema = (ObjectSchema) SchemaParser.parse("""
                    {"type": "object", "required": ["c"]}
                    """).getRoot();

            // when
            var result = ObjectGenerator.computeImpliedProperties("a",
                    Map.of(),
                    Map.of("a", depSchema));

            // then
            assertThat(result).containsExactlyInAnyOrder("a", "c");
        }

        @Test
        void mergesDependentRequiredFromDependentSchemaWithExisting() {
            var depSchema = (ObjectSchema) SchemaParser.parse("""
                    {"type": "object", "dependentRequired": {"b": ["d"]}}
                    """).getRoot();

            // when
            var result = ObjectGenerator.computeImpliedProperties("a",
                    Map.of("a", List.of("b"), "b", List.of("c")),
                    Map.of("a", depSchema));

            // then
            assertThat(result).containsExactlyInAnyOrder("a", "b", "c", "d");
        }

        @Test
        void includesDependentRequiredFromDependentSchema() {
            var depSchema = (ObjectSchema) SchemaParser.parse("""
                    {"type": "object", "dependentRequired": {"a": ["c"]}}
                    """).getRoot();

            // when
            var result = ObjectGenerator.computeImpliedProperties("a",
                    Map.of("a", List.of("b")),
                    Map.of("b", depSchema));

            // then
            assertThat(result).containsExactlyInAnyOrder("a", "b", "c");
        }
    }

    private static ObjectGenerator objectGenerator(String json) {
        var document = SchemaParser.parse(json);
        return new ObjectGenerator(new GeneratorContext(document, new Random(42)), (ObjectSchema) document.getRoot());
    }

    private static ObjectGenerator objectGeneratorWithAdditionalProperties(String json) {
        var document = SchemaParser.parse(json);
        var config = new GeneratorConfig(false, true, 2, 4, Map.of(), Map.of(), ValueConstraints.forExhaustive());
        var context = new GeneratorContext(document, new Random(42), config);
        return new ObjectGenerator(context, (ObjectSchema) document.getRoot());
    }

    @Nested
    class FocusNovelty {

        @Test
        void focusStaysOnAPropertyUntilItStopsCommittingNewNoveltyThenMovesToTheNext() {
            var document = SchemaParser.parse("""
                    {
                        "type": "object",
                        "properties": {
                            "a": {"type": "string"},
                            "b1": {"type": "boolean"},
                            "b2": {"type": "boolean"}
                        },
                        "required": ["a"]
                    }
                    """);
            var context = new GeneratorContext(document, new Random(1));
            var generator = new ObjectGenerator(context, (ObjectSchema) document.getRoot());

            // when
            // Runs 1-2 (MIN/MAX_PROPERTIES) are discarded below. FOCUS then
            // drives b1, then b2, each staying focused until its windowed
            // novelty score (fraction of its 5 most recent visits that were
            // new) hits zero - 7 visits each, since one novel visit already
            // banked during MAX_PROPERTIES has to age out of the window
            // first. Once both are exhausted, FOCUS skips and falls through
            // to RANDOM within that same call ("b1"); advancing the cycle
            // twice in one call starts the next call at MIN_PROPERTIES
            // ("neither").
            var focused = generateRuns(context, generator, 18).stream()
                    .skip(2)
                    .map(obj -> obj.containsKey("b1") ? "b1" : obj.containsKey("b2") ? "b2" : "neither")
                    .toList();

            // then
            assertThat(focused).containsExactly(
                    "b1", "b1", "b1", "b1", "b1", "b1", "b1",
                    "b2", "b2", "b2", "b2", "b2", "b2", "b2",
                    "b1", "neither");
        }

        private static List<Map<String, Object>> generateRuns(GeneratorContext context, ObjectGenerator generator, int runs) {
            var results = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < runs; i++) {
                context.startRun();
                results.add(generator.generate());
                context.completeRun();
            }
            return results;
        }
    }
}
