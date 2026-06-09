package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.parser.SchemaParser;

class AllOfGeneratorTest {

    @Nested
    class ValidMerges {

        @Test
        void singleSubSchemaPassThrough() {
            var generator = allOfGenerator("""
                    {
                        "allOf": [
                            {"type": "string"}
                        ]
                    }
                    """);

            // when
            var value = generator.generate();

            // then
            assertThat(value).isInstanceOf(String.class);
        }

        @Test
        void mergesStringLengthConstraints() {
            var generator = allOfGenerator("""
                    {
                        "allOf": [
                            {"type": "string", "minLength": 5},
                            {"type": "string", "maxLength": 5}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(20)
                    .map(String.class::cast)
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.length() == 5);
        }

        @Test
        void mergesNumericBounds() {
            var generator = allOfGenerator("""
                    {
                        "allOf": [
                            {"type": "integer", "minimum": 10},
                            {"type": "integer", "maximum": 20}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(50)
                    .map(Long.class::cast)
                    .toList();

            // then
            assertThat(values).allMatch(v -> v >= 10 && v <= 20);
        }

        @Test
        void mergesTypedWithUntypedCrossCutting() {
            var generator = allOfGenerator("""
                    {
                        "allOf": [
                            {"type": "string"},
                            {"enum": ["alpha", "beta", "gamma"]}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(10)
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.equals("alpha") || v.equals("beta") || v.equals("gamma"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void mergesObjectSchemas() {
            var generator = allOfGenerator("""
                    {
                        "allOf": [
                            {
                                "type": "object",
                                "properties": {"a": {"type": "string"}},
                                "required": ["a"]
                            },
                            {
                                "type": "object",
                                "properties": {"b": {"type": "integer"}},
                                "required": ["b"]
                            }
                        ]
                    }
                    """);

            // when
            var value = (java.util.Map<String, Object>) generator.generate();

            // then
            assertThat(value).containsOnlyKeys("a", "b");
            assertThat(value.get("a")).isInstanceOf(String.class);
            assertThat(value.get("b")).isInstanceOf(Long.class);
        }

        @Test
        void mergesEnumsByIntersection() {
            var generator = allOfGenerator("""
                    {
                        "allOf": [
                            {"enum": ["a", "b", "c"]},
                            {"enum": ["b", "c", "d"]}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(10)
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.equals("b") || v.equals("c"));
        }

        @Test
        void mergesMultipleOfAsLcm() {
            var generator = allOfGenerator("""
                    {
                        "allOf": [
                            {"type": "integer", "multipleOf": 3},
                            {"type": "integer", "multipleOf": 5}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(20)
                    .map(Long.class::cast)
                    .toList();

            // then
            assertThat(values).allMatch(v -> v % 15 == 0);
        }

        @Test
        void mergesParentSiblingConstraints() {
            var generator = allOfGenerator("""
                    {
                        "type": "integer",
                        "minimum": 10,
                        "allOf": [
                            {"type": "integer", "maximum": 20},
                            {"type": "integer", "multipleOf": 2}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(50)
                    .map(Long.class::cast)
                    .toList();

            // then
            assertThat(values).allMatch(v -> v >= 10 && v <= 20 && v % 2 == 0);
        }

        @Test
        @SuppressWarnings("unchecked")
        void mergesArraySchemas() {
            var generator = allOfGenerator("""
                    {
                        "allOf": [
                            {"type": "array", "minItems": 2, "items": {"type": "string"}},
                            {"type": "array", "maxItems": 4, "items": {"type": "string"}}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(20)
                    .map(v -> (java.util.List<Object>) v)
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.size() >= 2 && v.size() <= 4);
            assertThat(values).allMatch(v -> v.stream().allMatch(String.class::isInstance));
        }
    }

    @Nested
    class InvalidComposition {

        @Test
        void conflictingTypesThrows() {
            var json = """
                    {
                        "allOf": [
                            {"type": "string"},
                            {"type": "integer"}
                        ]
                    }
                    """;

            // when / then
            assertThatThrownBy(() -> allOfGenerator(json))
                    .isInstanceOf(UnsatisfiableSchemaException.class)
                    .hasMessageContaining("StringSchema")
                    .hasMessageContaining("NumericSchema");
        }

        @Test
        void emptyAllOfThrows() {
            var json = """
                    { "allOf": [] }
                    """;

            // when / then
            assertThatThrownBy(() -> allOfGenerator(json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("allOf");
        }

        @Test
        void disjointEnumsThrows() {
            var json = """
                    {
                        "allOf": [
                            {"enum": ["a", "b"]},
                            {"enum": ["c", "d"]}
                        ]
                    }
                    """;

            // when / then
            assertThatThrownBy(() -> allOfGenerator(json))
                    .isInstanceOf(UnsatisfiableSchemaException.class)
                    .hasMessageContaining("enum");
        }

        @Test
        void conflictingPatternsThrows() {
            var json = """
                    {
                        "allOf": [
                            {"type": "string", "pattern": "[a-z]+"},
                            {"type": "string", "pattern": "[A-Z]+"}
                        ]
                    }
                    """;

            // when / then
            assertThatThrownBy(() -> allOfGenerator(json))
                    .isInstanceOf(UnsatisfiableSchemaException.class)
                    .hasMessageContaining("pattern");
        }

        @Test
        void refInsideAllOfThrows() {
            var json = """
                    {
                        "definitions": {"Foo": {"type": "string"}},
                        "allOf": [
                            {"type": "string"},
                            {"$ref": "#/definitions/Foo"}
                        ]
                    }
                    """;

            // when / then
            assertThatThrownBy(() -> allOfGenerator(json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("$ref");
        }

        @Test
        void nestedOneOfInsideAllOfThrows() {
            var json = """
                    {
                        "allOf": [
                            {"type": "string"},
                            {"oneOf": [{"type": "string"}, {"type": "integer"}]}
                        ]
                    }
                    """;

            // when / then
            assertThatThrownBy(() -> allOfGenerator(json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("oneOf");
        }

        @Test
        void nestedAllOfInsideAllOfThrows() {
            var json = """
                    {
                        "allOf": [
                            {"type": "string"},
                            {"allOf": [{"type": "string", "minLength": 3}]}
                        ]
                    }
                    """;

            // when / then
            assertThatThrownBy(() -> allOfGenerator(json))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("allOf");
        }
    }

    private static AllOfGenerator allOfGenerator(String json) {
        var document = SchemaParser.parse(json);
        return new AllOfGenerator(
                new GeneratorContext(document, new Random(42)),
                document.getRoot());
    }
}
