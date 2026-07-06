package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
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

        @Test
        void mergesNumericBounds() {
            var generator = generatorFor("""
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
            var generator = generatorFor("""
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
            var generator = generatorFor("""
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
        void mergesConstWithCompatibleEnum() {
            var generator = generatorFor("""
                    {
                        "allOf": [
                            {"const": "beta"},
                            {"enum": ["alpha", "beta", "gamma"]}
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(10)
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.equals("beta"));
        }

        @Test
        void mergesEnumsByIntersection() {
            var generator = generatorFor("""
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
            var generator = generatorFor("""
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
        @SuppressWarnings("unchecked")
        void mergesArraySchemas() {
            var generator = generatorFor("""
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

        @Test
        @SuppressWarnings("unchecked")
        void mergesAllRefBranches() {
            var generator = generatorFor("""
                    {
                        "definitions": {
                            "HasId": {
                                "type": "object",
                                "properties": {"id": {"type": "integer"}},
                                "required": ["id"]
                            },
                            "HasName": {
                                "type": "object",
                                "properties": {"name": {"type": "string"}},
                                "required": ["name"]
                            }
                        },
                        "allOf": [
                            {"$ref": "#/definitions/HasId"},
                            {"$ref": "#/definitions/HasName"}
                        ]
                    }
                    """);

            // when
            var value = (java.util.Map<String, Object>) generator.generate();

            // then
            assertThat(value).containsKey("id");
            assertThat(value.get("id")).isInstanceOf(Long.class);
            assertThat(value).containsKey("name");
            assertThat(value.get("name")).isInstanceOf(String.class);
        }

        @Test
        @SuppressWarnings("unchecked")
        void additionalPropertiesFalseMergedThroughAllOf() {
            var generator = generatorFor("""
                    {
                        "allOf": [
                            {
                                "type": "object",
                                "properties": {"a": {"type": "string"}},
                                "required": ["a"],
                                "additionalProperties": false
                            },
                            {
                                "type": "object",
                                "properties": {"b": {"type": "integer"}}
                            }
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(20)
                    .map(v -> (java.util.Map<String, Object>) v)
                    .toList();

            // then
            assertThat(values).allSatisfy(obj -> {
                assertThat(obj).containsKey("a");
                assertThat(obj.get("a")).isInstanceOf(String.class);
            });
            assertThat(values).anyMatch(obj -> obj.containsKey("b"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void selfReferentialRefMergesOtherBranches() {
            var generator = generatorFor("""
                    {
                        "type": "object",
                        "properties": {
                            "value": {"type": "string"},
                            "tag": {"type": "string"}
                        },
                        "required": ["value"],
                        "allOf": [
                            {"$ref": "#"},
                            {
                                "type": "object",
                                "required": ["tag"]
                            }
                        ]
                    }
                    """);

            // when
            var value = (java.util.Map<String, Object>) generator.generate();

            // then
            assertThat(value).containsKey("value");
            assertThat(value).containsKey("tag");
        }

        @Test
        void conflictingConstValuesThrows() {
            // when / then
            assertThatThrownBy(() -> generatorFor("""
                    {
                        "allOf": [
                            {"const": "hello"},
                            {"const": "world"}
                        ]
                    }
                    """))
                    .isInstanceOf(UnsatisfiableSchemaException.class);
        }

        @Test
        void disjointEnumsThrows() {
            // when / then
            assertThatThrownBy(() -> generatorFor("""
                    {
                        "allOf": [
                            {"enum": ["a", "b"]},
                            {"enum": ["c", "d"]}
                        ]
                    }
                    """))
                    .isInstanceOf(UnsatisfiableSchemaException.class);
        }

        @Test
        void constNotInEnumThrows() {
            // when / then
            assertThatThrownBy(() -> generatorFor("""
                    {
                        "allOf": [
                            {"const": "delta"},
                            {"enum": ["alpha", "beta", "gamma"]}
                        ]
                    }
                    """))
                    .isInstanceOf(UnsatisfiableSchemaException.class);
        }

        @Test
        void conflictingPatternsKeepsLeftPattern() {
            // SchemaMerger keeps the left pattern on conflict (regex
            // intersection isn't implementable in general) and allOf is not
            // re-validated against its original branches (unlike oneOf/anyOf)
            // since doing so would conflict with this codebase's deliberate
            // union-then-restrict additionalProperties semantics for allOf --
            // see additionalPropertiesFalseMergedThroughAllOf. So this is a
            // permanent best-effort behavior, not a caught error.
            var generator = generatorFor("""
                    {
                        "allOf": [
                            {"type": "string", "pattern": "[a-z]+"},
                            {"type": "string", "pattern": "[A-Z]+"}
                        ]
                    }
                    """);

            // when
            var value = (String) generator.generate();

            // then
            assertThat(value).matches("[a-z]+");
        }

        @Test
        void nestedOneOfInsideAllOfIsSupported() {
            var generator = generatorFor("""
                    {
                        "allOf": [
                            {"type": "string"},
                            {"oneOf": [{"type": "string", "minLength": 1}, {"type": "string", "maxLength": 10}]}
                        ]
                    }
                    """);

            // when
            var value = generator.generate();

            // then
            assertThat(value).isInstanceOf(String.class);
        }

        @Test
        void nestedAllOfInsideAllOfIsSupported() {
            var generator = generatorFor("""
                    {
                        "allOf": [
                            {"type": "string"},
                            {"allOf": [{"type": "string", "minLength": 3}]}
                        ]
                    }
                    """);

            // when
            var value = generator.generate();

            // then
            assertThat(value).isInstanceOf(String.class);
            assertThat(((String) value).length()).isGreaterThanOrEqualTo(3);
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
            assertThat(observed).isEqualTo(
                    List.of(Long.class, String.class, Long.class, Long.class, String.class));
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
        void skipsUnsatisfiableNestedBranch() {
            var generator = generatorFor("""
                    {
                        "oneOf": [
                            {"type": "string"},
                            {
                                "type": "null",
                                "oneOf": [
                                    {"type": "string"},
                                    {"type": "integer"}
                                ]
                            }
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(20)
                    .toList();

            // then
            assertThat(values).allMatch(v -> v instanceof String);
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

        @Test
        @SuppressWarnings("unchecked")
        void overlappingPermissiveObjectBranchesEachMatchExactlyOneBranch() {
            // Neither branch forbids the other's property (no
            // additionalProperties: false), so a naively-generated value
            // can satisfy both -- disambiguation must break the tie.
            var document = SchemaParser.parse("""
                    {
                        "oneOf": [
                            {"type": "object", "properties": {"a": {"type": "string"}}},
                            {"type": "object", "properties": {"b": {"type": "string"}}}
                        ]
                    }
                    """);
            var context = new GeneratorContext(document, new Random(42));
            var generator = new AnyOfAllOfOneOfGenerator(context, document.getRoot());
            var validator = new SchemaValidator(context);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(30)
                    .map(v -> (Map<String, Object>) v)
                    .toList();

            // then
            assertThat(values).allMatch(v -> validator.satisfies(v, document.getRoot()));
        }

        @Test
        void patternDiscriminatedBranchesEachMatchExactlyOneBranch() {
            // The zero-branches-valid case from issue #66: without
            // validation, the generator ignores the branch-specific
            // patterns entirely and produces an unconstrained string.
            var document = SchemaParser.parse("""
                    {
                        "type": "object",
                        "required": ["file"],
                        "properties": {"file": {"type": "string"}},
                        "oneOf": [
                            {"properties": {"file": {"pattern": "\\\\.css$"}}},
                            {"properties": {"file": {"pattern": "\\\\.js$"}}}
                        ]
                    }
                    """);
            var context = new GeneratorContext(document, new Random(42));
            var generator = new AnyOfAllOfOneOfGenerator(context, document.getRoot());
            var validator = new SchemaValidator(context);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(30)
                    .toList();

            // then
            assertThat(values).allMatch(v -> validator.satisfies(v, document.getRoot()));
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
        void skipsUnsatisfiableNestedBranch() {
            var generator = generatorFor("""
                    {
                        "oneOf": [
                            {"type": "string"},
                            {
                                "type": "null",
                                "anyOf": [
                                    {"type": "string"},
                                    {"type": "integer"}
                                ]
                            }
                        ]
                    }
                    """);

            // when
            var values = Stream.generate(generator::generate)
                    .limit(20)
                    .toList();

            // then
            assertThat(values).allMatch(v -> v instanceof String);
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

        @Test
        void fallsBackToSmallerSubsetWhenMergeIsUnsatisfiable() {
            var generator = generatorFor("""
                    {
                        "anyOf": [
                            {"type": "string"},
                            {"type": "integer"}
                        ]
                    }
                    """);
            generator.generate();
            generator.generate();

            // when
            var values = Stream.generate(generator::generate).limit(50).toList();

            // then
            assertThat(values).hasSize(50);
            assertThat(values).allMatch(v -> v instanceof String || v instanceof Number);
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
