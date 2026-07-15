package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;
import se.plilja.jsonschemagen.internal.model.SchemaDocument;
import se.plilja.jsonschemagen.internal.parser.SchemaParser;

class SchemaValidatorTest {

    @Nested
    class StringSchemaValidation {

        @Test
        void stringValueSatisfiesStringSchema() {
            var document = SchemaParser.parse("""
                    {"type": "string"}
                    """);

            // when
            var result = createValidator(document).satisfies("abc", document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void nonStringValueFailsStringSchema() {
            var document = SchemaParser.parse("""
                    {"type": "string"}
                    """);

            // when
            var result = createValidator(document).satisfies(5, document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void stringWithinLengthBoundsSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"type": "string", "minLength": 2, "maxLength": 4}
                    """);

            // when
            var result = createValidator(document).satisfies("abc", document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void stringShorterThanMinLengthFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "string", "minLength": 2}
                    """);

            // when
            var result = createValidator(document).satisfies("a", document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void stringLongerThanMaxLengthFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "string", "maxLength": 2}
                    """);

            // when
            var result = createValidator(document).satisfies("abc", document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void stringMatchingPatternSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"type": "string", "pattern": "\\\\.css$"}
                    """);

            // when
            var result = createValidator(document).satisfies("style.css", document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void stringNotMatchingPatternFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "string", "pattern": "\\\\.css$"}
                    """);

            // when
            var result = createValidator(document).satisfies("style.js", document.getRoot());

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class NumericSchemaValidation {

        @Test
        void numberWithinRangeSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"type": "integer", "minimum": 1, "maximum": 10}
                    """);

            // when
            var result = createValidator(document).satisfies(5L, document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void numberBelowMinimumFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "integer", "minimum": 1}
                    """);

            // when
            var result = createValidator(document).satisfies(0L, document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void numberAboveMaximumFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "integer", "maximum": 10}
                    """);

            // when
            var result = createValidator(document).satisfies(11L, document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void numberEqualToExclusiveMinimumFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "integer", "exclusiveMinimum": 5}
                    """);

            // when
            var result = createValidator(document).satisfies(5L, document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void numberEqualToExclusiveMaximumFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "integer", "exclusiveMaximum": 5}
                    """);

            // when
            var result = createValidator(document).satisfies(5L, document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void numberNotMultipleOfFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "integer", "multipleOf": 5}
                    """);

            // when
            var result = createValidator(document).satisfies(7L, document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void nonIntegerValueFailsIntegerSchema() {
            var document = SchemaParser.parse("""
                    {"type": "integer"}
                    """);

            // when
            var result = createValidator(document).satisfies(1.5, document.getRoot());

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class ConstAndEnumValidation {

        @Test
        void valueMatchingConstSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"const": "fixed"}
                    """);

            // when
            var result = createValidator(document).satisfies("fixed", document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void valueNotMatchingConstFailsSchema() {
            var document = SchemaParser.parse("""
                    {"const": "fixed"}
                    """);

            // when
            var result = createValidator(document).satisfies("other", document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void valueInEnumSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"enum": ["a", "b"]}
                    """);

            // when
            var result = createValidator(document).satisfies("a", document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void valueNotInEnumFailsSchema() {
            var document = SchemaParser.parse("""
                    {"enum": ["a", "b"]}
                    """);

            // when
            var result = createValidator(document).satisfies("c", document.getRoot());

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class ObjectSchemaValidation {

        @Test
        void objectWithValidPropertiesSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}
                    """);

            // when
            var result = createValidator(document).satisfies(Map.of("name", "abc"), document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void objectMissingRequiredPropertyFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}
                    """);

            // when
            var result = createValidator(document).satisfies(Map.of(), document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void objectWithPropertyViolatingItsSchemaFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "object", "properties": {"name": {"type": "string", "minLength": 3}}}
                    """);

            // when
            var result = createValidator(document).satisfies(Map.of("name", "ab"), document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void objectWithDisallowedAdditionalPropertyFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "object", "properties": {"name": {"type": "string"}}, "additionalProperties": false}
                    """);

            // when
            var result = createValidator(document).satisfies(Map.of("name", "abc", "extra", "x"), document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void objectWithinPropertyCountBoundsSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"type": "object", "minProperties": 1, "maxProperties": 2}
                    """);

            // when
            var result = createValidator(document).satisfies(Map.of("a", 1), document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void objectWithTooFewPropertiesFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "object", "minProperties": 2}
                    """);

            // when
            var result = createValidator(document).satisfies(Map.of("a", 1), document.getRoot());

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class ArraySchemaValidation {

        @Test
        void arrayWithValidItemsSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"type": "array", "items": {"type": "string"}}
                    """);

            // when
            var result = createValidator(document).satisfies(List.of("a", "b"), document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void arrayWithInvalidItemFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "array", "items": {"type": "string"}}
                    """);

            // when
            var result = createValidator(document).satisfies(List.of("a", 1), document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void arrayWithinLengthBoundsSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"type": "array", "minItems": 1, "maxItems": 2}
                    """);

            // when
            var result = createValidator(document).satisfies(List.of("a"), document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void arrayShorterThanMinItemsFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "array", "minItems": 2}
                    """);

            // when
            var result = createValidator(document).satisfies(List.of("a"), document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void arrayViolatingPrefixItemSchemaFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "array", "prefixItems": [{"type": "string"}, {"type": "integer"}]}
                    """);

            // when
            var result = createValidator(document).satisfies(List.of("a", "not-an-integer"), document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void arrayNotContainingRequiredElementFailsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "array", "contains": {"type": "integer"}}
                    """);

            // when
            var result = createValidator(document).satisfies(List.of("a", "b"), document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void arrayContainingRequiredElementSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"type": "array", "contains": {"type": "integer"}}
                    """);

            // when
            var result = createValidator(document).satisfies(List.of("a", 1), document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void arrayWithDuplicateElementsFailsUniqueItemsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "array", "uniqueItems": true}
                    """);

            // when
            var result = createValidator(document).satisfies(List.of("a", "a"), document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void arrayWithDistinctElementsSatisfiesUniqueItemsSchema() {
            var document = SchemaParser.parse("""
                    {"type": "array", "uniqueItems": true}
                    """);

            // when
            var result = createValidator(document).satisfies(List.of("a", "b"), document.getRoot());

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    class RefValidation {

        @Test
        void valueSatisfyingRefTargetSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {
                        "type": "object",
                        "properties": {"tag": {"$ref": "#/definitions/Tag"}},
                        "definitions": {"Tag": {"type": "string", "minLength": 3}}
                    }
                    """);
            var validator = createValidator(document);
            var tagSchema = ((ObjectSchema) document.getRoot()).getProperties().get("tag");

            // when
            var result = validator.satisfies("abcd", tagSchema);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void valueViolatingRefTargetFailsSchema() {
            var document = SchemaParser.parse("""
                    {
                        "type": "object",
                        "properties": {"tag": {"$ref": "#/definitions/Tag"}},
                        "definitions": {"Tag": {"type": "string", "minLength": 3}}
                    }
                    """);
            var validator = createValidator(document);
            var tagSchema = ((ObjectSchema) document.getRoot()).getProperties().get("tag");

            // when
            var result = validator.satisfies("a", tagSchema);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @Timeout(5)
        void cyclicSelfReferencingSchemaDoesNotInfiniteLoop() {
            var document = SchemaParser.parse("""
                    {"$ref": "#"}
                    """);
            var validator = createValidator(document);

            // when
            var result = validator.satisfies("anything", document.getRoot());

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    class CombiningKeywordValidation {

        @Test
        void valueSatisfyingAllAllOfBranchesSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"allOf": [{"type": "string", "minLength": 2}, {"type": "string", "maxLength": 5}]}
                    """);

            // when
            var result = createValidator(document).satisfies("abc", document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void valueViolatingOneAllOfBranchFailsSchema() {
            var document = SchemaParser.parse("""
                    {"allOf": [{"type": "string", "minLength": 2}, {"type": "string", "maxLength": 5}]}
                    """);

            // when
            var result = createValidator(document).satisfies("a", document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void valueSatisfyingAtLeastOneBranchPerAnyOfClauseSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"anyOf": [{"type": "string", "minLength": 5}, {"type": "string", "pattern": "^a"}]}
                    """);

            // when
            var result = createValidator(document).satisfies("abc", document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void valueSatisfyingNoBranchOfAnyOfClauseFailsSchema() {
            var document = SchemaParser.parse("""
                    {"anyOf": [{"type": "string", "minLength": 5}, {"type": "string", "pattern": "^z"}]}
                    """);

            // when
            var result = createValidator(document).satisfies("abc", document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void valueSatisfyingExactlyOneBranchPerOneOfClauseSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"oneOf": [{"pattern": "\\\\.css$"}, {"pattern": "\\\\.js$"}]}
                    """);

            // when
            var result = createValidator(document).satisfies("style.css", document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void valueSatisfyingZeroBranchesOfOneOfClauseFailsSchema() {
            var document = SchemaParser.parse("""
                    {"oneOf": [{"pattern": "\\\\.css$"}, {"pattern": "\\\\.js$"}]}
                    """);

            // when
            var result = createValidator(document).satisfies("style.png", document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void valueSatisfyingMultipleBranchesOfOneOfClauseFailsSchema() {
            var document = SchemaParser.parse("""
                    {"oneOf": [{"type": "object", "properties": {"a": {"type": "string"}}},
                               {"type": "object", "properties": {"b": {"type": "string"}}}]}
                    """);

            // "a" is an unconstrained additional property under the second
            // branch too, so both branches match -- oneOf requires exactly one.

            // when
            var result = createValidator(document).satisfies(Map.of("a", "x"), document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void multiGroupOneOfRequiresExactlyOneMatchPerGroup() {
            var a = SchemaParser.parse("""
                    {"oneOf": [{"pattern": "\\\\.css$"}, {"pattern": "\\\\.js$"}]}
                    """).getRoot();
            var b = SchemaParser.parse("""
                    {"oneOf": [{"pattern": "^src/"}, {"pattern": "^lib/"}]}
                    """).getRoot();
            var merged = SchemaMerger.merge(List.of(a, b));
            var document = new SchemaDocument(merged, Map.of());
            var validator = createValidator(document);

            // when
            var srcCss = validator.satisfies("src/style.css", merged);
            var cssOnly = validator.satisfies("style.css", merged);
            var srcTs = validator.satisfies("src/app.ts", merged);

            // then
            assertThat(srcCss).isTrue();
            assertThat(cssOnly).isFalse();
            assertThat(srcTs).isFalse();
        }

        @Test
        void multiGroupAnyOfRequiresAtLeastOneMatchPerGroup() {
            var a = SchemaParser.parse("""
                    {"anyOf": [{"pattern": "^a"}, {"pattern": "^b"}]}
                    """).getRoot();
            var b = SchemaParser.parse("""
                    {"anyOf": [{"pattern": "x$"}, {"pattern": "y$"}]}
                    """).getRoot();
            var merged = SchemaMerger.merge(List.of(a, b));
            var document = new SchemaDocument(merged, Map.of());
            var validator = createValidator(document);

            // when
            var ax = validator.satisfies("ax", merged);
            var az = validator.satisfies("az", merged);
            var cx = validator.satisfies("cx", merged);

            // then
            assertThat(ax).isTrue();
            assertThat(az).isFalse();
            assertThat(cx).isFalse();
        }

        @Test
        void valueSatisfyingExactlyOneOfTwoDiscriminatedBranchesSatisfiesSchema() {
            var document = SchemaParser.parse("""
                    {"oneOf": [{"type": "object", "properties": {"a": {"type": "string"}}, "additionalProperties": false},
                               {"type": "object", "properties": {"b": {"type": "string"}}, "additionalProperties": false}]}
                    """);
            var validator = createValidator(document);

            // when
            var result = validator.satisfies(Map.of("a", "x"), document.getRoot());

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    class ConditionalValidation {

        private static final String IF_THEN_ELSE = """
                {
                    "type": "object",
                    "properties": {"status": {"type": "string"}},
                    "if": {"properties": {"status": {"const": "ok"}}},
                    "then": {"required": ["data"]},
                    "else": {"required": ["error"]}
                }
                """;

        @Test
        void valueMatchingIfAndThenSatisfiesSchema() {
            var document = SchemaParser.parse(IF_THEN_ELSE);

            // when
            var result = createValidator(document)
                    .satisfies(Map.of("status", "ok", "data", "payload"), document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void valueMatchingIfButViolatingThenFailsSchema() {
            var document = SchemaParser.parse(IF_THEN_ELSE);

            // when
            var result = createValidator(document)
                    .satisfies(Map.of("status", "ok"), document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void valueFailingIfAndSatisfyingElseSatisfiesSchema() {
            var document = SchemaParser.parse(IF_THEN_ELSE);

            // when
            var result = createValidator(document)
                    .satisfies(Map.of("status", "bad", "error", "boom"), document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void valueFailingIfAndViolatingElseFailsSchema() {
            var document = SchemaParser.parse(IF_THEN_ELSE);

            // when
            var result = createValidator(document)
                    .satisfies(Map.of("status", "bad"), document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void missingThenLeavesTheMatchingBranchUnconstrained() {
            // if + else only: a value that matches if has no extra constraint.
            var document = SchemaParser.parse("""
                    {
                        "type": "object",
                        "properties": {"status": {"type": "string"}},
                        "if": {"properties": {"status": {"const": "ok"}}},
                        "else": {"required": ["error"]}
                    }
                    """);

            // when
            var result = createValidator(document)
                    .satisfies(Map.of("status", "ok"), document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void missingElseLeavesTheNonMatchingBranchUnconstrained() {
            // if + then only: a value that fails if has no extra constraint.
            var document = SchemaParser.parse("""
                    {
                        "type": "object",
                        "properties": {"status": {"type": "string"}},
                        "if": {"properties": {"status": {"const": "ok"}}},
                        "then": {"required": ["data"]}
                    }
                    """);

            // when
            var result = createValidator(document)
                    .satisfies(Map.of("status", "bad"), document.getRoot());

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    class NotValidation {

        private static final String INTEGER_NOT_ZERO = """
                {"type": "integer", "not": {"const": 0}}
                """;

        @Test
        void valueMatchingNotSubschemaFailsSchema() {
            var document = SchemaParser.parse(INTEGER_NOT_ZERO);

            // when
            var result = createValidator(document).satisfies(0, document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void valueNotMatchingNotSubschemaSatisfiesSchema() {
            var document = SchemaParser.parse(INTEGER_NOT_ZERO);

            // when
            var result = createValidator(document).satisfies(1, document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void numericValueMatchingNotConstFailsAcrossNumericTypes() {
            // The value's Java numeric type (BigDecimal) differs from the const's
            // (Integer); they are still the same JSON number and must be rejected.
            var document = SchemaParser.parse("""
                    {"type": "integer", "not": {"const": 1}}
                    """);

            // when
            var result = createValidator(document).satisfies(BigDecimal.ONE, document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void bareNotRejectsForbiddenType() {
            var document = SchemaParser.parse("""
                    {"not": {"type": "string"}}
                    """);

            // when
            var result = createValidator(document).satisfies("forbidden", document.getRoot());

            // then
            assertThat(result).isFalse();
        }

        @Test
        void bareNotAcceptsOtherTypes() {
            var document = SchemaParser.parse("""
                    {"not": {"type": "string"}}
                    """);

            // when
            var result = createValidator(document).satisfies(42, document.getRoot());

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    class OverriddenValueExemption {

        @Test
        void overriddenValueSatisfiesSchemaItWouldOtherwiseViolate() {
            var document = SchemaParser.parse("""
                    {"type": "integer"}
                    """);

            // when
            var result = createValidator(document)
                    .satisfies(new OverriddenValue("not-a-number"), document.getRoot());

            // then
            assertThat(result).isTrue();
        }

        @Test
        void overriddenValueNestedInObjectIsExempt() {
            var document = SchemaParser.parse("""
                    {"type": "object", "properties": {"n": {"type": "integer"}}, "required": ["n"]}
                    """);
            var value = Map.of("n", new OverriddenValue("not-a-number"));

            // when
            var result = createValidator(document).satisfies(value, document.getRoot());

            // then
            assertThat(result).isTrue();
        }
    }

    private static SchemaValidator createValidator(SchemaDocument document) {
        return new SchemaValidator(new GeneratorContext(document, new Random(42)));
    }
}
