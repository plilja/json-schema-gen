package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.parser.SchemaParser;

class SchemaValidatorTest {

    @Nested
    class StringSchemaValidation {

        @Test
        void stringValueSatisfiesStringSchema() {
            var fixture = fixtureFor("""
                    {"type": "string"}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("abc", fixture.schema)).isTrue();
        }

        @Test
        void nonStringValueFailsStringSchema() {
            var fixture = fixtureFor("""
                    {"type": "string"}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(5, fixture.schema)).isFalse();
        }

        @Test
        void stringWithinLengthBoundsSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"type": "string", "minLength": 2, "maxLength": 4}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("abc", fixture.schema)).isTrue();
        }

        @Test
        void stringShorterThanMinLengthFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "string", "minLength": 2}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("a", fixture.schema)).isFalse();
        }

        @Test
        void stringLongerThanMaxLengthFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "string", "maxLength": 2}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("abc", fixture.schema)).isFalse();
        }

        @Test
        void stringMatchingPatternSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"type": "string", "pattern": "\\\\.css$"}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("style.css", fixture.schema)).isTrue();
        }

        @Test
        void stringNotMatchingPatternFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "string", "pattern": "\\\\.css$"}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("style.js", fixture.schema)).isFalse();
        }
    }

    @Nested
    class NumericSchemaValidation {

        @Test
        void numberWithinRangeSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"type": "integer", "minimum": 1, "maximum": 10}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(5L, fixture.schema)).isTrue();
        }

        @Test
        void numberBelowMinimumFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "integer", "minimum": 1}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(0L, fixture.schema)).isFalse();
        }

        @Test
        void numberAboveMaximumFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "integer", "maximum": 10}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(11L, fixture.schema)).isFalse();
        }

        @Test
        void numberEqualToExclusiveMinimumFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "integer", "exclusiveMinimum": 5}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(5L, fixture.schema)).isFalse();
        }

        @Test
        void numberEqualToExclusiveMaximumFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "integer", "exclusiveMaximum": 5}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(5L, fixture.schema)).isFalse();
        }

        @Test
        void numberNotMultipleOfFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "integer", "multipleOf": 5}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(7L, fixture.schema)).isFalse();
        }

        @Test
        void nonIntegerValueFailsIntegerSchema() {
            var fixture = fixtureFor("""
                    {"type": "integer"}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(1.5, fixture.schema)).isFalse();
        }
    }

    @Nested
    class ConstAndEnumValidation {

        @Test
        void valueMatchingConstSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"const": "fixed"}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("fixed", fixture.schema)).isTrue();
        }

        @Test
        void valueNotMatchingConstFailsSchema() {
            var fixture = fixtureFor("""
                    {"const": "fixed"}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("other", fixture.schema)).isFalse();
        }

        @Test
        void valueInEnumSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"enum": ["a", "b"]}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("a", fixture.schema)).isTrue();
        }

        @Test
        void valueNotInEnumFailsSchema() {
            var fixture = fixtureFor("""
                    {"enum": ["a", "b"]}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("c", fixture.schema)).isFalse();
        }
    }

    @Nested
    class ObjectSchemaValidation {

        @Test
        void objectWithValidPropertiesSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(Map.of("name", "abc"), fixture.schema)).isTrue();
        }

        @Test
        void objectMissingRequiredPropertyFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(Map.of(), fixture.schema)).isFalse();
        }

        @Test
        void objectWithPropertyViolatingItsSchemaFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "object", "properties": {"name": {"type": "string", "minLength": 3}}}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(Map.of("name", "ab"), fixture.schema)).isFalse();
        }

        @Test
        void objectWithDisallowedAdditionalPropertyFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "object", "properties": {"name": {"type": "string"}}, "additionalProperties": false}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(Map.of("name", "abc", "extra", "x"), fixture.schema)).isFalse();
        }

        @Test
        void objectWithinPropertyCountBoundsSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"type": "object", "minProperties": 1, "maxProperties": 2}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(Map.of("a", 1), fixture.schema)).isTrue();
        }

        @Test
        void objectWithTooFewPropertiesFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "object", "minProperties": 2}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(Map.of("a", 1), fixture.schema)).isFalse();
        }
    }

    @Nested
    class ArraySchemaValidation {

        @Test
        void arrayWithValidItemsSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"type": "array", "items": {"type": "string"}}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(List.of("a", "b"), fixture.schema)).isTrue();
        }

        @Test
        void arrayWithInvalidItemFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "array", "items": {"type": "string"}}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(List.of("a", 1), fixture.schema)).isFalse();
        }

        @Test
        void arrayWithinLengthBoundsSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"type": "array", "minItems": 1, "maxItems": 2}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(List.of("a"), fixture.schema)).isTrue();
        }

        @Test
        void arrayShorterThanMinItemsFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "array", "minItems": 2}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(List.of("a"), fixture.schema)).isFalse();
        }

        @Test
        void arrayViolatingPrefixItemSchemaFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "array", "prefixItems": [{"type": "string"}, {"type": "integer"}]}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(List.of("a", "not-an-integer"), fixture.schema)).isFalse();
        }

        @Test
        void arrayNotContainingRequiredElementFailsSchema() {
            var fixture = fixtureFor("""
                    {"type": "array", "contains": {"type": "integer"}}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(List.of("a", "b"), fixture.schema)).isFalse();
        }

        @Test
        void arrayContainingRequiredElementSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"type": "array", "contains": {"type": "integer"}}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(List.of("a", 1), fixture.schema)).isTrue();
        }
    }

    @Nested
    class RefValidation {

        @Test
        void valueSatisfyingRefTargetSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {
                        "type": "object",
                        "properties": {"tag": {"$ref": "#/definitions/Tag"}},
                        "definitions": {"Tag": {"type": "string", "minLength": 3}}
                    }
                    """);
            var tagSchema = ((ObjectSchema) fixture.schema).getProperties().get("tag");

            // when / then
            assertThat(fixture.validator.satisfies("abcd", tagSchema)).isTrue();
        }

        @Test
        void valueViolatingRefTargetFailsSchema() {
            var fixture = fixtureFor("""
                    {
                        "type": "object",
                        "properties": {"tag": {"$ref": "#/definitions/Tag"}},
                        "definitions": {"Tag": {"type": "string", "minLength": 3}}
                    }
                    """);
            var tagSchema = ((ObjectSchema) fixture.schema).getProperties().get("tag");

            // when / then
            assertThat(fixture.validator.satisfies("a", tagSchema)).isFalse();
        }

        @Test
        @Timeout(5)
        void cyclicSelfReferencingSchemaDoesNotInfiniteLoop() {
            var fixture = fixtureFor("""
                    {"$ref": "#"}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("anything", fixture.schema)).isTrue();
        }
    }

    @Nested
    class CombiningKeywordValidation {

        @Test
        void valueSatisfyingAllAllOfBranchesSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"allOf": [{"type": "string", "minLength": 2}, {"type": "string", "maxLength": 5}]}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("abc", fixture.schema)).isTrue();
        }

        @Test
        void valueViolatingOneAllOfBranchFailsSchema() {
            var fixture = fixtureFor("""
                    {"allOf": [{"type": "string", "minLength": 2}, {"type": "string", "maxLength": 5}]}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("a", fixture.schema)).isFalse();
        }

        @Test
        void valueSatisfyingAtLeastOneBranchPerAnyOfClauseSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"anyOf": [{"type": "string", "minLength": 5}, {"type": "string", "pattern": "^a"}]}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("abc", fixture.schema)).isTrue();
        }

        @Test
        void valueSatisfyingNoBranchOfAnyOfClauseFailsSchema() {
            var fixture = fixtureFor("""
                    {"anyOf": [{"type": "string", "minLength": 5}, {"type": "string", "pattern": "^z"}]}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("abc", fixture.schema)).isFalse();
        }

        @Test
        void valueSatisfyingExactlyOneBranchPerOneOfClauseSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"oneOf": [{"pattern": "\\\\.css$"}, {"pattern": "\\\\.js$"}]}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("style.css", fixture.schema)).isTrue();
        }

        @Test
        void valueSatisfyingZeroBranchesOfOneOfClauseFailsSchema() {
            var fixture = fixtureFor("""
                    {"oneOf": [{"pattern": "\\\\.css$"}, {"pattern": "\\\\.js$"}]}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies("style.png", fixture.schema)).isFalse();
        }

        @Test
        void valueSatisfyingMultipleBranchesOfOneOfClauseFailsSchema() {
            var fixture = fixtureFor("""
                    {"oneOf": [{"type": "object", "properties": {"a": {"type": "string"}}},
                               {"type": "object", "properties": {"b": {"type": "string"}}}]}
                    """);

            // "a" is an unconstrained additional property under the second
            // branch too, so both branches match -- oneOf requires exactly one.

            // when / then
            assertThat(fixture.validator.satisfies(Map.of("a", "x"), fixture.schema)).isFalse();
        }

        @Test
        void valueSatisfyingExactlyOneOfTwoDiscriminatedBranchesSatisfiesSchema() {
            var fixture = fixtureFor("""
                    {"oneOf": [{"type": "object", "properties": {"a": {"type": "string"}}, "additionalProperties": false},
                               {"type": "object", "properties": {"b": {"type": "string"}}, "additionalProperties": false}]}
                    """);

            // when / then
            assertThat(fixture.validator.satisfies(Map.of("a", "x"), fixture.schema)).isTrue();
        }
    }

    private static Fixture fixtureFor(String json) {
        var document = SchemaParser.parse(json);
        var validator = new SchemaValidator(new GeneratorContext(document, new Random(42)));
        return new Fixture(validator, document.getRoot());
    }

    private record Fixture(SchemaValidator validator, Schema schema) {
    }
}
