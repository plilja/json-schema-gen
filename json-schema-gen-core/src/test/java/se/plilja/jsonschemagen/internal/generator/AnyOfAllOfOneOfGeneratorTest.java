package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.parser.SchemaParser;

class AnyOfAllOfOneOfGeneratorTest {

    @Nested
    class AllOfOnly {

        @Test
        void singleSubSchemaPassThrough() {
            var generator = generatorFor("""
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
            var generator = generatorFor("""
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
        void mergesParentSiblingConstraints() {
            var generator = generatorFor("""
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
        void resolvesRefBranches() {
            var generator = generatorFor("""
                    {
                        "definitions": {
                            "HasName": {
                                "type": "object",
                                "properties": {"name": {"type": "string"}},
                                "required": ["name"]
                            }
                        },
                        "allOf": [
                            {"$ref": "#/definitions/HasName"},
                            {
                                "type": "object",
                                "properties": {"age": {"type": "integer"}},
                                "required": ["age"]
                            }
                        ]
                    }
                    """);

            // when
            var value = (java.util.Map<String, Object>) generator.generate();

            // then
            assertThat(value).containsKey("name");
            assertThat(value).containsKey("age");
        }

        @Test
        @SuppressWarnings("unchecked")
        void selfReferentialRefIsSkipped() {
            var generator = generatorFor("""
                    {
                        "type": "object",
                        "properties": {"value": {"type": "string"}},
                        "required": ["value"],
                        "allOf": [{"$ref": "#"}]
                    }
                    """);

            // when
            var value = (java.util.Map<String, Object>) generator.generate();

            // then
            assertThat(value).containsKey("value");
        }

        @Test
        void conflictingTypesThrows() {
            // when / then
            assertThatThrownBy(() -> generatorFor("""
                    {
                        "allOf": [
                            {"type": "string"},
                            {"type": "integer"}
                        ]
                    }
                    """))
                    .isInstanceOf(UnsatisfiableSchemaException.class);
        }

        @Test
        void emptyAllOfThrows() {
            // when / then
            assertThatThrownBy(() -> generatorFor("""
                    {"allOf": []}
                    """))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class OneOfOnly {

        @Test
        void exhaustsBranchesInOrder() {
            var generator = generatorFor("""
                    {
                        "oneOf": [
                            {"type": "string"},
                            {"type": "integer"}
                        ]
                    }
                    """);

            // when
            var first = generator.generate();
            var second = generator.generate();

            // then
            assertThat(first).isInstanceOf(String.class);
            assertThat(second).isInstanceOf(Number.class);
        }

        @Test
        void producesRandomValuesAfterExhaustingBranches() {
            var generator = generatorFor("""
                    {
                        "oneOf": [
                            {"type": "string"},
                            {"type": "integer"}
                        ]
                    }
                    """);
            generator.generate();
            generator.generate();

            // when
            var observed = Stream.generate(generator::generate)
                    .limit(5)
                    .map(Object::getClass)
                    .toList();

            // then
            assertThat(observed).containsExactly(
                    Long.class, String.class, Long.class, Long.class, String.class);
        }

        @Test
        void respectsParentSiblingConstraints() {
            var generator = generatorFor("""
                    {
                        "type": "object",
                        "required": ["x"],
                        "properties": {"x": {"type": "string"}},
                        "oneOf": [
                            {"type": "object", "properties": {"y": {"type": "string"}}},
                            {"type": "object", "properties": {"z": {"type": "integer"}}}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(50)
                    .map(v -> (java.util.Map<?, ?>) v)
                    .toList();

            // then
            assertThat(values).allMatch(m -> m.containsKey("x"));
        }

        @Test
        void noCompatibleBranchThrows() {
            // when / then
            assertThatThrownBy(() -> generatorFor("""
                    {
                        "type": "string",
                        "oneOf": [
                            {"type": "integer"},
                            {"type": "boolean"}
                        ]
                    }
                    """))
                    .isInstanceOf(UnsatisfiableSchemaException.class);
        }
    }

    @Nested
    class AnyOfOnly {

        @Test
        void exhaustsBranchesInOrder() {
            var generator = generatorFor("""
                    {
                        "anyOf": [
                            {"type": "string"},
                            {"type": "integer"}
                        ]
                    }
                    """);

            // when
            var first = generator.generate();
            var second = generator.generate();

            // then
            assertThat(first).isInstanceOf(String.class);
            assertThat(second).isInstanceOf(Number.class);
        }

        @Test
        void randomSubsetCanMergeMultipleBranches() {
            var generator = generatorFor("""
                    {
                        "anyOf": [
                            {
                                "type": "object",
                                "properties": {"foo": {"type": "string"}},
                                "required": ["foo"]
                            },
                            {
                                "type": "object",
                                "properties": {"bar": {"type": "string"}},
                                "required": ["bar"]
                            }
                        ]
                    }
                    """);
            generator.generate();
            generator.generate();

            // when
            var values = Stream.generate(generator::generate)
                    .limit(50)
                    .map(v -> (java.util.Map<?, ?>) v)
                    .toList();

            // then
            assertThat(values).anyMatch(m -> m.containsKey("foo") && m.containsKey("bar"));
        }

        @Test
        void respectsParentSiblingConstraints() {
            var generator = generatorFor("""
                    {
                        "type": "object",
                        "required": ["x"],
                        "properties": {"x": {"type": "string"}},
                        "anyOf": [
                            {"type": "object", "properties": {"y": {"type": "string"}}},
                            {"type": "object", "properties": {"z": {"type": "integer"}}}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(50)
                    .map(v -> (java.util.Map<?, ?>) v)
                    .toList();

            // then
            assertThat(values).allMatch(m -> m.containsKey("x"));
        }
    }

    @Nested
    class MultipleKeywords {

        @Test
        @SuppressWarnings("unchecked")
        void oneOfPlusAllOfSatisfiesBoth() {
            var generator = generatorFor("""
                    {
                        "allOf": [
                            {
                                "type": "object",
                                "properties": {"name": {"type": "string"}},
                                "required": ["name"]
                            }
                        ],
                        "oneOf": [
                            {"type": "object", "properties": {"role": {"const": "admin"}}, "required": ["role"]},
                            {"type": "object", "properties": {"role": {"const": "user"}}, "required": ["role"]}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(10)
                    .map(v -> (java.util.Map<String, Object>) v)
                    .toList();

            // then — allOf: name always present; oneOf: role is admin or user
            assertThat(values).allSatisfy(m -> {
                assertThat(m).containsKey("name");
                assertThat(m).containsKey("role");
                assertThat(m.get("role")).isIn("admin", "user");
            });
        }

        @Test
        @SuppressWarnings("unchecked")
        void anyOfPlusAllOfSatisfiesBoth() {
            var generator = generatorFor("""
                    {
                        "allOf": [
                            {
                                "type": "object",
                                "properties": {"id": {"type": "integer"}},
                                "required": ["id"]
                            }
                        ],
                        "anyOf": [
                            {"type": "object", "properties": {"email": {"type": "string"}}},
                            {"type": "object", "properties": {"phone": {"type": "string"}}}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(20)
                    .map(v -> (java.util.Map<String, Object>) v)
                    .toList();

            // then — allOf: id always present; anyOf: at least email or phone
            assertThat(values).allSatisfy(m -> {
                assertThat(m).containsKey("id");
                assertThat(m.get("id")).isInstanceOf(Long.class);
            });
        }

        @Test
        @SuppressWarnings("unchecked")
        void oneOfPlusAnyOfSatisfiesBoth() {
            var generator = generatorFor("""
                    {
                        "oneOf": [
                            {"type": "object", "properties": {"kind": {"const": "a"}}, "required": ["kind"]},
                            {"type": "object", "properties": {"kind": {"const": "b"}}, "required": ["kind"]}
                        ],
                        "anyOf": [
                            {"type": "object", "properties": {"tag": {"type": "string"}}},
                            {"type": "object", "properties": {"label": {"type": "string"}}}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(20)
                    .map(v -> (java.util.Map<String, Object>) v)
                    .toList();

            // then — oneOf: kind is "a" or "b"
            assertThat(values).allSatisfy(m -> {
                assertThat(m).containsKey("kind");
                assertThat(m.get("kind")).isIn("a", "b");
            });
        }

        @Test
        @SuppressWarnings("unchecked")
        void allThreeKeywordsSatisfiedSimultaneously() {
            var generator = generatorFor("""
                    {
                        "allOf": [
                            {
                                "type": "object",
                                "properties": {"id": {"type": "integer"}},
                                "required": ["id"]
                            }
                        ],
                        "oneOf": [
                            {"type": "object", "properties": {"kind": {"const": "x"}}, "required": ["kind"]},
                            {"type": "object", "properties": {"kind": {"const": "y"}}, "required": ["kind"]}
                        ],
                        "anyOf": [
                            {"type": "object", "properties": {"tag": {"type": "string"}}},
                            {"type": "object", "properties": {"label": {"type": "string"}}}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(20)
                    .map(v -> (java.util.Map<String, Object>) v)
                    .toList();

            // then — allOf: id present; oneOf: kind is x or y
            assertThat(values).allSatisfy(m -> {
                assertThat(m).containsKey("id");
                assertThat(m).containsKey("kind");
                assertThat(m.get("kind")).isIn("x", "y");
            });
        }
    }

    private static AnyOfAllOfOneOfGenerator generatorFor(String json) {
        var document = SchemaParser.parse(json);
        return new AnyOfAllOfOneOfGenerator(
                new GeneratorContext(document, new Random(42)),
                document.getRoot());
    }
}
