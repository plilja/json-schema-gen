package io.github.gjuton.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.ArraySchema;
import io.github.gjuton.internal.model.NumericSchema;
import io.github.gjuton.internal.model.ObjectSchema;
import io.github.gjuton.internal.model.Schema;
import io.github.gjuton.internal.model.UnsatisfiableSchema;
import io.github.gjuton.internal.parser.SchemaParser;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SchemaMergerTest {

    @Nested
    class StringMerges {

        @Test
        void mergesMinAndMaxLength() {
            var a = readSchema("""
                    {"type": "string", "minLength": 5}
                    """);
            var b = readSchema("""
                    {"type": "string", "maxLength": 10}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "string", "minLength": 5, "maxLength": 10}
                    """));
        }

        @Test
        void takesTighterBounds() {
            var a = readSchema("""
                    {"type": "string", "minLength": 3, "maxLength": 20}
                    """);
            var b = readSchema("""
                    {"type": "string", "minLength": 5, "maxLength": 10}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "string", "minLength": 5, "maxLength": 10}
                    """));
        }

        @Test
        void mergesPatternWithConstraints() {
            var a = readSchema("""
                    {"type": "string", "pattern": "[a-z]+"}
                    """);
            var b = readSchema("""
                    {"type": "string", "minLength": 3}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "string", "minLength": 3, "pattern": "[a-z]+"}
                    """));
        }

        @Test
        void mergesFormatWithConstraints() {
            var a = readSchema("""
                    {"type": "string", "format": "email"}
                    """);
            var b = readSchema("""
                    {"type": "string", "minLength": 3}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "string", "minLength": 3, "format": "email"}
                    """));
        }

        @Test
        void conflictingFormatsThrows() {
            var a = readSchema("""
                    {"type": "string", "format": "email"}
                    """);
            var b = readSchema("""
                    {"type": "string", "format": "uuid"}
                    """);

            // when / then
            assertThatThrownBy(() -> SchemaMerger.merge(List.of(a, b)))
                    .isInstanceOf(UnsatisfiableSchemaException.class);
        }

        @Test
        void conflictingPatternsKeepsLeftPattern() {
            // Regex intersection isn't implementable in general, so the
            // merger can't detect whether the two patterns are truly
            // incompatible. It keeps the left pattern as a best-effort
            // generation guide; a generated value that violates the
            // dropped pattern is caught later by validation, not here.
            var a = readSchema("""
                    {"type": "string", "pattern": "[a-z]+"}
                    """);
            var b = readSchema("""
                    {"type": "string", "pattern": "[A-Z]+"}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "string", "pattern": "[a-z]+"}
                    """));
        }
    }

    @Nested
    class NumericMerges {

        @Test
        void mergesMinAndMax() {
            var a = readSchema("""
                    {"type": "integer", "minimum": 10}
                    """);
            var b = readSchema("""
                    {"type": "integer", "maximum": 20}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "integer", "minimum": 10, "maximum": 20}
                    """));
        }

        @Test
        void mergesExclusiveBounds() {
            var a = readSchema("""
                    {"type": "integer", "exclusiveMinimum": 0}
                    """);
            var b = readSchema("""
                    {"type": "integer", "exclusiveMaximum": 100}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "integer", "exclusiveMinimum": 0, "exclusiveMaximum": 100}
                    """));
        }

        @Test
        void mergesMultipleOf() {
            var a = readSchema("""
                    {"type": "integer", "multipleOf": 3}
                    """);
            var b = readSchema("""
                    {"type": "integer", "multipleOf": 5}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "integer", "multipleOf": 15}
                    """));
        }

        @Test
        void integerAndNumberMergeToInteger() {
            var a = readSchema("""
                    {"type": "integer", "minimum": 5}
                    """);
            var b = readSchema("""
                    {"type": "number", "maximum": 20}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isInstanceOf(NumericSchema.class);
            var numeric = (NumericSchema) merged;
            assertThat(numeric.isInteger()).isTrue();
        }

        @Test
        void numberAndIntegerMergeToInteger() {
            var a = readSchema("""
                    {"type": "number", "minimum": 5}
                    """);
            var b = readSchema("""
                    {"type": "integer", "maximum": 20}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isInstanceOf(NumericSchema.class);
            var numeric = (NumericSchema) merged;
            assertThat(numeric.isInteger()).isTrue();
        }
    }

    @Nested
    class ObjectMerges {

        @Test
        void mergesPropertiesAndRequired() {
            var a = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "required": ["a"]
                    }
                    """);
            var b = readSchema("""
                    {
                        "type": "object",
                        "properties": {"b": {"type": "integer"}},
                        "required": ["b"]
                    }
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}, "b": {"type": "integer"}},
                        "required": ["a", "b"]
                    }
                    """));
        }

        @Test
        void mergesMinPropertiesTakingStricter() {
            var a = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "minProperties": 1
                    }
                    """);
            var b = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "minProperties": 3
                    }
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "minProperties": 3
                    }
                    """));
        }

        @Test
        void mergesMaxPropertiesTakingStricter() {
            var a = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "maxProperties": 5
                    }
                    """);
            var b = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "maxProperties": 3
                    }
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "maxProperties": 3
                    }
                    """));
        }

        @Test
        void unionsNonOverlappingPatternProperties() {
            var a = readSchema("""
                    {
                        "type": "object",
                        "patternProperties": {"^a": {"type": "string"}}
                    }
                    """);
            var b = readSchema("""
                    {
                        "type": "object",
                        "patternProperties": {"^b": {"type": "integer"}}
                    }
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {
                        "type": "object",
                        "patternProperties": {"^a": {"type": "string"}, "^b": {"type": "integer"}}
                    }
                    """));
        }

        @Test
        void mergesOverlappingPatternPropertiesKeys() {
            var a = readSchema("""
                    {
                        "type": "object",
                        "patternProperties": {"^a": {"type": "string", "minLength": 3}}
                    }
                    """);
            var b = readSchema("""
                    {
                        "type": "object",
                        "patternProperties": {"^a": {"type": "string", "maxLength": 10}}
                    }
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {
                        "type": "object",
                        "patternProperties": {"^a": {"type": "string", "minLength": 3, "maxLength": 10}}
                    }
                    """));
        }

        @Test
        void mergesDependentRequiredFromBothSides() {
            var a = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}, "b": {"type": "string"}},
                        "dependentRequired": {"a": ["b"]}
                    }
                    """);
            var b = readSchema("""
                    {
                        "type": "object",
                        "properties": {"c": {"type": "string"}, "d": {"type": "string"}},
                        "dependentRequired": {"c": ["d"]}
                    }
                    """);

            // when
            var merged = (ObjectSchema) SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged.getDependentRequired())
                    .containsEntry("a", List.of("b"))
                    .containsEntry("c", List.of("d"));
        }

        @Test
        void mergesDependentSchemasFromBothSides() {
            var a = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "dependentSchemas": {"a": {"properties": {"b": {"type": "string"}}, "required": ["b"]}}
                    }
                    """);
            var b = readSchema("""
                    {
                        "type": "object",
                        "properties": {"c": {"type": "string"}},
                        "dependentSchemas": {"c": {"properties": {"d": {"type": "string"}}, "required": ["d"]}}
                    }
                    """);

            // when
            var merged = (ObjectSchema) SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged.getDependentSchemas()).containsKeys("a", "c");
        }

        @Test
        void additionalPropertiesFalseWinsOverAbsent() {
            var a = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "additionalProperties": false
                    }
                    """);
            var b = readSchema("""
                    {
                        "type": "object",
                        "properties": {"b": {"type": "integer"}}
                    }
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}, "b": {"type": "integer"}},
                        "additionalProperties": false
                    }
                    """));
        }

        @Test
        void additionalPropertiesFalseWinsOverTrue() {
            var a = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "additionalProperties": true
                    }
                    """);
            var b = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "additionalProperties": false
                    }
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "additionalProperties": false
                    }
                    """));
        }

        @Test
        void additionalPropertiesFalseWinsOverSchema() {
            var a = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "additionalProperties": {"type": "string"}
                    }
                    """);
            var b = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "additionalProperties": false
                    }
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "additionalProperties": false
                    }
                    """));
        }

        @Test
        void additionalPropertiesSchemaWinsOverTrue() {
            var a = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "additionalProperties": true
                    }
                    """);
            var b = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "additionalProperties": {"type": "integer"}
                    }
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "additionalProperties": {"type": "integer"}
                    }
                    """));
        }

        @Test
        void additionalPropertiesTwoSchemasMerged() {
            var a = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "additionalProperties": {"type": "string", "minLength": 3}
                    }
                    """);
            var b = readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "additionalProperties": {"type": "string", "maxLength": 10}
                    }
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {
                        "type": "object",
                        "properties": {"a": {"type": "string"}},
                        "additionalProperties": {"type": "string", "minLength": 3, "maxLength": 10}
                    }
                    """));
        }
    }

    @Nested
    class ArrayMerges {

        @Test
        void mergesItemBounds() {
            var a = readSchema("""
                    {"type": "array", "minItems": 2, "items": {"type": "string"}}
                    """);
            var b = readSchema("""
                    {"type": "array", "maxItems": 5, "items": {"type": "string"}}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "array", "minItems": 2, "maxItems": 5, "items": {"type": "string"}}
                    """));
        }

        @Test
        void uniqueItemsTrueOnEitherSideWins() {
            var a = readSchema("""
                    {"type": "array", "items": {"type": "string"}, "uniqueItems": true}
                    """);
            var b = readSchema("""
                    {"type": "array", "items": {"type": "string"}}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "array", "items": {"type": "string"}, "uniqueItems": true}
                    """));
        }

        @Test
        void mergesItemSchemas() {
            var a = readSchema("""
                    {"type": "array", "items": {"type": "string", "minLength": 3}}
                    """);
            var b = readSchema("""
                    {"type": "array", "items": {"type": "string", "maxLength": 10}}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "array", "items": {"type": "string", "minLength": 3, "maxLength": 10}}
                    """));
        }

        @Test
        void containsMergesRecursivelyWhenBothPresent() {
            var a = readSchema("""
                    {"type": "array", "contains": {"type": "string", "minLength": 3}}
                    """);
            var b = readSchema("""
                    {"type": "array", "contains": {"type": "string", "maxLength": 10}}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "array", "contains": {"type": "string", "minLength": 3, "maxLength": 10}}
                    """));
        }

        @Test
        void mergesTuplePrefixSchemasPairwise() {
            var a = readSchema("""
                    {"type": "array", "items": [
                        {"type": "string", "minLength": 3},
                        {"type": "integer"}
                    ]}
                    """);
            var b = readSchema("""
                    {"type": "array", "items": [
                        {"type": "string", "maxLength": 10},
                        {"type": "integer", "minimum": 0}
                    ]}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "array", "prefixItems": [
                        {"type": "string", "minLength": 3, "maxLength": 10},
                        {"type": "integer", "minimum": 0}
                    ]}
                    """));
        }

        @Test
        void mergesPrefixItemsSyntaxPairwise() {
            var a = readSchema("""
                    {"type": "array", "prefixItems": [
                        {"type": "string", "minLength": 1}
                    ], "items": false}
                    """);
            var b = readSchema("""
                    {"type": "array", "prefixItems": [
                        {"type": "string", "maxLength": 10}
                    ], "items": false}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            var expected = readSchema("""
                    {"type": "array", "prefixItems": [
                        {"type": "string", "minLength": 1, "maxLength": 10}
                    ], "items": false, "additionalItems": false}
                    """);
            assertThat(merged).isEqualTo(expected);
        }

        @Test
        void mergesAdditionalItemsFalseWins() {
            var a = readSchema("""
                    {"type": "array", "items": [{"type": "string"}], "additionalItems": false}
                    """);
            var b = readSchema("""
                    {"type": "array", "items": [{"type": "string"}], "additionalItems": true}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "array", "prefixItems": [{"type": "string"}], "additionalItems": false}
                    """));
        }

        @Test
        void mergesAdditionalItemsSchemas() {
            var a = readSchema("""
                    {"type": "array", "items": [{"type": "string"}],
                     "additionalItems": {"type": "integer", "minimum": 0}}
                    """);
            var b = readSchema("""
                    {"type": "array", "items": [{"type": "string"}],
                     "additionalItems": {"type": "integer", "maximum": 100}}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            var mergedArray = (ArraySchema) merged;
            assertThat(mergedArray.getItemSchema()).isEqualTo(readSchema("""
                    {"type": "integer", "minimum": 0, "maximum": 100}
                    """));
            assertThat(mergedArray.areAdditionalItemsAllowed()).isTrue();
        }

        @Test
        void tupleOnOneSidePreserved() {
            var a = readSchema("""
                    {"type": "array", "items": [{"type": "string"}, {"type": "integer"}]}
                    """);
            var b = readSchema("""
                    {"type": "array", "minItems": 2}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "array", "prefixItems": [{"type": "string"}, {"type": "integer"}],
                     "minItems": 2}
                    """));
        }

        @Test
        void mergesItemsFalsePreservesUnsatisfiable() {
            var a = readSchema("""
                    {"type": "array", "items": false}
                    """);
            var b = readSchema("""
                    {"type": "array", "items": false}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isInstanceOf(ArraySchema.class);
            assertThat(((ArraySchema) merged).getItemSchema())
                    .isInstanceOf(UnsatisfiableSchema.class);
        }

        @Test
        void mergesItemsFalseWithItemsSchemaUnsatisfiableWins() {
            var a = readSchema("""
                    {"type": "array", "items": false}
                    """);
            var b = readSchema("""
                    {"type": "array", "items": {"type": "string"}}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isInstanceOf(ArraySchema.class);
            assertThat(((ArraySchema) merged).getItemSchema())
                    .isInstanceOf(UnsatisfiableSchema.class);
        }

        @Test
        void containsCoalescesWhenOnlyOneSideHasIt() {
            var a = readSchema("""
                    {"type": "array", "contains": {"const": "x"}}
                    """);
            var b = readSchema("""
                    {"type": "array", "items": {"type": "string"}}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "array", "items": {"type": "string"}, "contains": {"const": "x"}}
                    """));
        }
    }

    @Nested
    class UntypedMerges {

        @Test
        void untypedMergesWithTyped() {
            var a = readSchema("""
                    {"type": "string", "minLength": 5}
                    """);
            var b = readSchema("""
                    {"enum": ["hello", "world"]}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "string", "minLength": 5, "enum": ["hello", "world"]}
                    """));
        }

        @Test
        void blankUntypedSeedKeepsOtherSidesRef() {
            var blank = readSchema("""
                    {}
                    """);
            var ref = readSchema("""
                    {"$ref": "#/definitions/Foo", "definitions": {"Foo": {"type": "string"}}}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(blank, ref));

            // then
            assertThat(merged.getRef()).isEqualTo("#/definitions/Foo");
        }

        @Test
        void refSurvivesWhenSeededFirst() {
            var ref = readSchema("""
                    {"$ref": "#/definitions/Foo", "definitions": {"Foo": {"type": "string"}}}
                    """);
            var blank = readSchema("""
                    {}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(ref, blank));

            // then
            assertThat(merged.getRef()).isEqualTo("#/definitions/Foo");
        }
    }

    @Nested
    class CrossCuttingMerges {

        @Test
        void mergesConstValues() {
            var a = readSchema("""
                    {"type": "string"}
                    """);
            var b = readSchema("""
                    {"const": "exact"}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"type": "string", "const": "exact"}
                    """));
        }

        @Test
        void conflictingConstThrows() {
            var a = readSchema("""
                    {"const": "hello"}
                    """);
            var b = readSchema("""
                    {"const": "world"}
                    """);

            // when / then
            assertThatThrownBy(() -> SchemaMerger.merge(List.of(a, b)))
                    .isInstanceOf(UnsatisfiableSchemaException.class);
        }

        @Test
        void mergesEnumsByIntersection() {
            var a = readSchema("""
                    {"enum": ["a", "b", "c"]}
                    """);
            var b = readSchema("""
                    {"enum": ["b", "c", "d"]}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged).isEqualTo(readSchema("""
                    {"enum": ["b", "c"]}
                    """));
        }

        @Test
        void disjointEnumsThrows() {
            var a = readSchema("""
                    {"enum": ["a", "b"]}
                    """);
            var b = readSchema("""
                    {"enum": ["c", "d"]}
                    """);

            // when / then
            assertThatThrownBy(() -> SchemaMerger.merge(List.of(a, b)))
                    .isInstanceOf(UnsatisfiableSchemaException.class);
        }
    }

    @Nested
    class CombiningKeywordPropagation {

        @Test
        void propagatesOneOfThroughMerge() {
            var a = readSchema("""
                    {"type": "string", "minLength": 3}
                    """);
            var b = readSchema("""
                    {"oneOf": [{"type": "string"}, {"type": "integer"}]}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged.getOneOf()).isNotNull();
            assertThat(merged.getOneOf()).hasSize(1);
            assertThat(merged.getOneOf().getFirst()).hasSize(2);
        }

        @Test
        void propagatesAllOfThroughMerge() {
            var a = readSchema("""
                    {"type": "string", "minLength": 3}
                    """);
            var b = readSchema("""
                    {"allOf": [{"type": "string", "maxLength": 10}]}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged.getAllOf()).isNotNull();
            assertThat(merged.getAllOf()).hasSize(1);
        }

        @Test
        void propagatesAnyOfThroughMerge() {
            var a = readSchema("""
                    {"type": "string", "minLength": 3}
                    """);
            var b = readSchema("""
                    {"anyOf": [{"type": "string"}, {"type": "integer"}]}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged.getAnyOf()).isNotNull();
            assertThat(merged.getAnyOf()).hasSize(1);
            assertThat(merged.getAnyOf().getFirst()).hasSize(2);
        }

        @Test
        void concatenatesAllOfFromBothSides() {
            var a = readSchema("""
                    {"allOf": [{"type": "string", "minLength": 3}]}
                    """);
            var b = readSchema("""
                    {"allOf": [{"type": "string", "maxLength": 10}]}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged.getAllOf()).hasSize(2);
        }

        @Test
        void concatenatesOneOfFromBothSides() {
            var a = readSchema("""
                    {"oneOf": [{"type": "string"}]}
                    """);
            var b = readSchema("""
                    {"oneOf": [{"type": "integer"}]}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged.getOneOf()).hasSize(2);
            assertThat(merged.getOneOf().get(0)).hasSize(1);
            assertThat(merged.getOneOf().get(1)).hasSize(1);
        }

        @Test
        void concatenatesAnyOfFromBothSides() {
            var a = readSchema("""
                    {"anyOf": [{"type": "string"}]}
                    """);
            var b = readSchema("""
                    {"anyOf": [{"type": "integer"}]}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged.getAnyOf()).hasSize(2);
            assertThat(merged.getAnyOf().get(0)).hasSize(1);
            assertThat(merged.getAnyOf().get(1)).hasSize(1);
        }

        @Test
        void propagatesConditionalFromLeftSide() {
            var a = readSchema("""
                    {
                        "if": {"properties": {"status": {"const": "ok"}}},
                        "then": {"required": ["data"]},
                        "else": {"required": ["error"]}
                    }
                    """);
            var b = readSchema("""
                    {"type": "object"}
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged.getConditionals()).containsExactly(
                    new Schema.Conditional(a.getIfSchema(), a.getThenSchema(), a.getElseSchema()));
        }

        @Test
        void propagatesConditionalFromRightSide() {
            var a = readSchema("""
                    {"type": "object"}
                    """);
            var b = readSchema("""
                    {
                        "if": {"properties": {"status": {"const": "ok"}}},
                        "then": {"required": ["data"]}
                    }
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged.getConditionals()).containsExactly(
                    new Schema.Conditional(b.getIfSchema(), b.getThenSchema(), null));
        }

        @Test
        void concatenatesConditionalsFromBothSides() {
            var a = readSchema("""
                    {
                        "if": {"properties": {"a": {"const": "x"}}},
                        "then": {"required": ["p"]}
                    }
                    """);
            var b = readSchema("""
                    {
                        "if": {"properties": {"b": {"const": "y"}}},
                        "then": {"required": ["q"]}
                    }
                    """);

            // when
            var merged = SchemaMerger.merge(List.of(a, b));

            // then
            assertThat(merged.getConditionals()).containsExactly(
                    new Schema.Conditional(a.getIfSchema(), a.getThenSchema(), null),
                    new Schema.Conditional(b.getIfSchema(), b.getThenSchema(), null));
        }
    }

    @Nested
    class IncompatibleTypes {

        @Test
        void stringAndIntegerThrows() {
            var a = readSchema("""
                    {"type": "string"}
                    """);
            var b = readSchema("""
                    {"type": "integer"}
                    """);

            // when / then
            assertThatThrownBy(() -> SchemaMerger.merge(List.of(a, b)))
                    .isInstanceOf(UnsatisfiableSchemaException.class);
        }
    }

    private static Schema readSchema(String json) {
        return SchemaParser.parse(json).getRoot();
    }
}
