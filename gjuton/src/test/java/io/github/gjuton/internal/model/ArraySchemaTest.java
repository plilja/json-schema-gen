package io.github.gjuton.internal.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gjuton.internal.parser.SchemaParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ArraySchemaTest {

    @Nested
    class AreAdditionalItemsAllowed {

        @Test
        void trueWhenNoTuple() {
            var schema = parseArray("""
                    {
                        "type": "array",
                        "items": {"type": "string"}
                    }
                    """);

            // then
            assertThat(schema.areAdditionalItemsAllowed()).isTrue();
        }

        @Test
        void trueWhenAdditionalItemsTrue() {
            var schema = parseArray("""
                    {
                        "type": "array",
                        "items": [{"type": "string"}],
                        "additionalItems": true
                    }
                    """);

            // then
            assertThat(schema.areAdditionalItemsAllowed()).isTrue();
        }

        @Test
        void falseWhenAdditionalItemsFalse() {
            var schema = parseArray("""
                    {
                        "type": "array",
                        "items": [{"type": "string"}],
                        "additionalItems": false
                    }
                    """);

            // then
            assertThat(schema.areAdditionalItemsAllowed()).isFalse();
        }

        @Test
        void falseWhenPrefixItemsWithItemsFalse() {
            var schema = parseArray("""
                    {
                        "type": "array",
                        "prefixItems": [{"type": "string"}],
                        "items": false
                    }
                    """);

            // then
            assertThat(schema.areAdditionalItemsAllowed()).isFalse();
        }

        @Test
        void trueWhenAdditionalItemsIsSchema() {
            var schema = parseArray("""
                    {
                        "type": "array",
                        "items": [{"type": "string"}],
                        "additionalItems": {"type": "integer"}
                    }
                    """);

            // then
            assertThat(schema.areAdditionalItemsAllowed()).isTrue();
        }

        @Test
        void trueWhenNothingSpecified() {
            var schema = parseArray("""
                    {
                        "type": "array"
                    }
                    """);

            // then
            assertThat(schema.areAdditionalItemsAllowed()).isTrue();
        }
    }

    @Nested
    class GetItemSchema {

        @Test
        void returnsUniformItemSchema() {
            var schema = parseArray("""
                    {
                        "type": "array",
                        "items": {"type": "string"}
                    }
                    """);

            // then
            assertThat(schema.getItemSchema()).isInstanceOf(StringSchema.class);
        }

        @Test
        void additionalItemsSchemaTakesPrecedence() {
            var schema = parseArray("""
                    {
                        "type": "array",
                        "items": [{"type": "string"}],
                        "additionalItems": {"type": "integer"}
                    }
                    """);

            // then
            assertThat(schema.getItemSchema()).isInstanceOf(NumericSchema.class);
        }

        @Test
        void nullWhenItemsIsTupleArray() {
            var schema = parseArray("""
                    {
                        "type": "array",
                        "items": [{"type": "string"}]
                    }
                    """);

            // then
            assertThat(schema.getItemSchema()).isNull();
        }

        @Test
        void nullWhenNothingSpecified() {
            var schema = parseArray("""
                    {
                        "type": "array"
                    }
                    """);

            // then
            assertThat(schema.getItemSchema()).isNull();
        }

        @Test
        void returnsItemsSchemaWhenPrefixItemsPresent() {
            var schema = parseArray("""
                    {
                        "type": "array",
                        "prefixItems": [{"type": "string"}],
                        "items": {"type": "integer"}
                    }
                    """);

            // then
            assertThat(schema.getItemSchema()).isInstanceOf(NumericSchema.class);
        }

        @Test
        void returnsUnsatisfiableWhenItemsFalse() {
            var schema = parseArray("""
                    {
                        "type": "array",
                        "items": false
                    }
                    """);

            // then
            assertThat(schema.getItemSchema()).isInstanceOf(UnsatisfiableSchema.class);
        }
    }

    @Nested
    class IsUniqueItems {

        @Test
        void trueWhenUniqueItemsTrue() {
            var schema = parseArray("""
                    {
                        "type": "array",
                        "items": {"type": "string"},
                        "uniqueItems": true
                    }
                    """);

            // then
            assertThat(schema.isUniqueItems()).isTrue();
        }

        @Test
        void falseWhenAbsent() {
            var schema = parseArray("""
                    {
                        "type": "array",
                        "items": {"type": "string"}
                    }
                    """);

            // then
            assertThat(schema.isUniqueItems()).isFalse();
        }
    }

    private static ArraySchema parseArray(String json) {
        return (ArraySchema) SchemaParser.parse(json).getRoot();
    }
}
