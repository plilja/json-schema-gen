package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.parser.SchemaParser;

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
        void conflictingPatternsThrows() {
            var a = readSchema("""
                    {"type": "string", "pattern": "[a-z]+"}
                    """);
            var b = readSchema("""
                    {"type": "string", "pattern": "[A-Z]+"}
                    """);

            // when / then
            assertThatThrownBy(() -> SchemaMerger.merge(List.of(a, b)))
                    .isInstanceOf(UnsatisfiableSchemaException.class);
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
