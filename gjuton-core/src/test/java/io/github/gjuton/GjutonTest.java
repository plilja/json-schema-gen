package io.github.gjuton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gjuton.api.GenerationMode;
import io.github.gjuton.api.Gjuton;
import io.github.gjuton.errors.UnsatisfiableSchemaException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GjutonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String INT_SCHEMA = """
            { "type": "integer", "minimum": 5, "maximum": 100 }""";
    private static final String OBJECT_SCHEMA = """
            {
              "type": "object",
              "properties": { "a": { "type": "integer" } },
              "required": ["a"]
            }""";
    private static final String CLOSED_OBJECT_SCHEMA = """
            {
              "type": "object",
              "properties": { "a": { "type": "integer" } },
              "required": ["a"],
              "additionalProperties": false
            }""";
    private static final String RECURSIVE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "child": { "$ref": "#" },
                "v": { "type": "integer" }
              }
            }""";

    @Test
    void unconfiguredMatchesExplicitExhaustive() {
        // when
        var unconfigured = generate(Gjuton.of(OBJECT_SCHEMA).withSeed(42L), 50);
        var explicit = generate(
                Gjuton.of(OBJECT_SCHEMA).withMode(GenerationMode.EXHAUSTIVE).withSeed(42L), 50);

        // then
        assertThat(unconfigured).isEqualTo(explicit);
    }

    @Test
    void exhaustiveModeEmitsBoundaryValuesFirst() {
        // when
        var gen = Gjuton.of(INT_SCHEMA).withSeed(1L);

        // then
        assertThat(gen.generate()).isEqualTo("5");
        assertThat(gen.generate()).isEqualTo("100");
    }

    @Test
    void randomOnlyModeSkipsBoundaryValues() {
        // when
        var gen = Gjuton.of(INT_SCHEMA).withMode(GenerationMode.RANDOM_ONLY).withSeed(1L);
        var values = generate(gen, 100);

        // then
        assertThat(values.get(0)).isNotEqualTo("5");
        assertThat(values).allSatisfy(v -> assertThat(Integer.parseInt(v)).isBetween(5, 100));
    }

    @Test
    void withModeLastCallWins() {
        // when
        var gen = Gjuton.of(INT_SCHEMA)
                .withMode(GenerationMode.RANDOM_ONLY)
                .withMode(GenerationMode.EXHAUSTIVE)
                .withSeed(1L);

        // then
        assertThat(gen.generate()).isEqualTo("5");
    }

    @Test
    void additionalPropertiesOffProducesOnlyDeclaredFields() {
        // when
        var gen = Gjuton.of(OBJECT_SCHEMA).withSeed(3L);
        var fieldNames = allFieldNames(gen, 100);

        // then
        assertThat(fieldNames).containsOnly("a");
    }

    @Test
    void additionalPropertiesOnAddsExtraFields() {
        // when
        var gen = Gjuton.of(OBJECT_SCHEMA).withAdditionalProperties().withSeed(3L);
        var fieldNames = allFieldNames(gen, 100);

        // then
        assertThat(fieldNames).contains("a");
        assertThat(fieldNames).hasSizeGreaterThan(1);
    }

    @Test
    void additionalPropertiesOnHonoursAdditionalPropertiesFalse() {
        // when
        var gen = Gjuton.of(CLOSED_OBJECT_SCHEMA).withAdditionalProperties().withSeed(3L);
        var fieldNames = allFieldNames(gen, 100);

        // then
        assertThat(fieldNames).containsOnly("a");
    }

    @Test
    void deepRecursionLimitsAllowDeeperNestingThanShallow() {
        // when
        int shallow = maxNestingDepth(
                Gjuton.of(RECURSIVE_SCHEMA).withRecursionLimitsShallow().withSeed(9L));
        int deep = maxNestingDepth(
                Gjuton.of(RECURSIVE_SCHEMA).withRecursionLimitsDeep().withSeed(9L));

        // then
        assertThat(deep).isGreaterThan(shallow);
    }

    @Test
    void withModeRejectsNull() {
        // then
        assertThatThrownBy(() -> Gjuton.of(INT_SCHEMA).withMode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withRecursionLimitsRejectsSoftBelowOne() {
        // then
        assertThatThrownBy(() -> Gjuton.of(INT_SCHEMA).withRecursionLimits(0, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withRecursionLimitsRejectsSoftAboveHard() {
        // then
        assertThatThrownBy(() -> Gjuton.of(INT_SCHEMA).withRecursionLimits(5, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> generate(Gjuton gen, int iterations) {
        var values = new ArrayList<String>(iterations);
        for (int i = 0; i < iterations; i++) {
            values.add(gen.generate());
        }
        return values;
    }

    private static List<String> allFieldNames(Gjuton gen, int iterations) {
        var names = new ArrayList<String>();
        for (int i = 0; i < iterations; i++) {
            var node = parse(gen.generate());
            node.fieldNames().forEachRemaining(names::add);
        }
        return names;
    }

    private static int maxNestingDepth(Gjuton gen) {
        int max = 0;
        for (int i = 0; i < 200; i++) {
            max = Math.max(max, nestingDepth(parse(gen.generate())));
        }
        return max;
    }

    private static int nestingDepth(JsonNode node) {
        var child = node.get("child");
        return child == null ? 0 : 1 + nestingDepth(child);
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    class Producers {

        private static final String TWO_FIELD_SCHEMA = """
                {
                  "type": "object",
                  "properties": { "role": { "type": "string" }, "n": { "type": "integer" } },
                  "required": ["role", "n"]
                }""";
        private static final String NESTED_SCHEMA = """
                {
                  "type": "object",
                  "properties": {
                    "a": {
                      "type": "object",
                      "properties": { "b": { "type": "string" } },
                      "required": ["b"]
                    }
                  },
                  "required": ["a"]
                }""";
        private static final String ARRAY_SCHEMA = """
                { "type": "array", "items": { "type": "integer" }, "minItems": 3 }""";

        @Test
        void producerReplacesFieldValue() {
            // when
            var gen = Gjuton.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withProducer("$.role", () -> "admin");

            // then
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("admin");
        }

        @Test
        void producerInvokedOnEachGenerate() {
            // when
            var counter = new int[] {0};
            var gen = Gjuton.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withProducer("$.role", () -> "user-" + counter[0]++);

            // then
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("user-0");
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("user-1");
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("user-2");
        }

        @Test
        void producerReturningBeanSerializesAsObject() {
            // when
            var gen = Gjuton.of(NESTED_SCHEMA).withSeed(1L)
                    .withProducer("$.a", () -> new Point(3, 4));

            // then
            var a = parse(gen.generate()).get("a");
            assertThat(a.get("x").asInt()).isEqualTo(3);
            assertThat(a.get("y").asInt()).isEqualTo(4);
        }

        @Test
        void producerOnNestedPathOverridesOnlyThatField() {
            // when
            var gen = Gjuton.of(NESTED_SCHEMA).withSeed(1L)
                    .withProducer("$.a.b", () -> "fixed");

            // then
            assertThat(parse(gen.generate()).get("a").get("b").asText()).isEqualTo("fixed");
        }

        @Test
        void producerOnArrayElementOverridesThatIndex() {
            // when
            var gen = Gjuton.of(ARRAY_SCHEMA).withSeed(1L)
                    .withProducer("$[0]", () -> 999);

            // then
            assertThat(parse(gen.generate()).get(0).asInt()).isEqualTo(999);
        }

        @Test
        void producerAtRootReplacesWholeValue() {
            // when
            var gen = Gjuton.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withProducer("$", () -> List.of("replaced"));

            // then
            var root = parse(gen.generate());
            assertThat(root.isArray()).isTrue();
            assertThat(root.get(0).asText()).isEqualTo("replaced");
        }

        @Test
        void producerBypassesGenerationOfUnsatisfiableField() {
            // given a schema whose required field can never be generated
            var schema = """
                    { "type": "object", "properties": { "x": false }, "required": ["x"] }""";

            // then generation fails without a producer
            assertThatThrownBy(() -> Gjuton.of(schema).withSeed(1L).generate())
                    .isInstanceOf(UnsatisfiableSchemaException.class);

            // when a producer supplies the value, the subtree is never generated
            var gen = Gjuton.of(schema).withSeed(1L)
                    .withProducer("$.x", () -> "supplied");

            // then
            assertThat(parse(gen.generate()).get("x").asText()).isEqualTo("supplied");
        }

        @Test
        void producerBypassesRequiredFieldWithNoSchema() {
            // given a required field the schema neither declares nor allows
            var schema = """
                    { "type": "object", "required": ["x"], "additionalProperties": false }""";

            // then generation fails without a producer
            assertThatThrownBy(() -> Gjuton.of(schema).withSeed(1L).generate())
                    .isInstanceOf(UnsatisfiableSchemaException.class);

            // when a producer supplies the value, the field is never resolved
            var gen = Gjuton.of(schema).withSeed(1L)
                    .withProducer("$.x", () -> "supplied");

            // then
            assertThat(parse(gen.generate()).get("x").asText()).isEqualTo("supplied");
        }

        @Test
        void overriddenValueIsNotValidatedAgainstItsSchema() {
            // given a validate-and-retry parent (anyOf) around an integer field,
            // overridden with a non-integer value
            var schema = """
                    {
                      "anyOf": [
                        { "type": "object", "properties": { "n": { "type": "integer" } }, "required": ["n"] }
                      ]
                    }""";
            var gen = Gjuton.of(schema).withSeed(1L)
                    .withProducer("$.n", () -> "not-a-number");

            // then the override survives validation and appears verbatim
            assertThat(parse(gen.generate()).get("n").asText()).isEqualTo("not-a-number");
        }

        @Test
        void producerOnUnvisitedPathNeverFires() {
            // when a producer targets a field the schema does not declare
            var fired = new boolean[] {false};
            var gen = Gjuton.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withProducer("$.absent", () -> {
                        fired[0] = true;
                        return "x";
                    });
            gen.generate();

            // then it is never invoked
            assertThat(fired[0]).isFalse();
        }

        @Test
        void withProducerLastCallWinsForSamePath() {
            // when
            var gen = Gjuton.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withProducer("$.role", () -> "first")
                    .withProducer("$.role", () -> "second");

            // then
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("second");
        }

        @Test
        void withProducerRejectsNullArguments() {
            // then
            assertThatThrownBy(() -> Gjuton.of(TWO_FIELD_SCHEMA).withProducer(null, () -> "x"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Gjuton.of(TWO_FIELD_SCHEMA).withProducer("$.role", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private record Point(int x, int y) {
        }
    }

    @Nested
    class ValueCoverage {

        @Test
        void startsAtZeroBeforeAnyGeneration() {
            // when
            var gen = Gjuton.of(INT_SCHEMA).withSeed(1L);

            // then
            assertThat(gen.valueCoverage()).isEqualTo(0.0);
        }

        @Test
        void booleanReachesOneAfterBothValuesAndRandom() {
            var gen = Gjuton.of("""
                    { "type": "boolean" }""").withSeed(1L);

            // when: both boundary values
            gen.generate();
            gen.generate();

            // then
            assertThat(gen.valueCoverage()).isLessThan(1.0);

            // when: one random value completes the set
            gen.generate();

            // then
            assertThat(gen.valueCoverage()).isEqualTo(1.0);
        }

        @Test
        void enumReachesOneAfterAllLiteralsEmitted() {
            var gen = Gjuton.of("""
                    { "enum": ["a", "b", "c"] }""").withSeed(1L);

            // when
            var coverages = new ArrayList<Double>();
            for (int i = 0; i < 3; i++) {
                gen.generate();
                coverages.add(gen.valueCoverage());
            }

            // then
            assertThat(coverages).containsExactly(1.0 / 3, 2.0 / 3, 1.0);
        }

        @Test
        void neverDecreasesAcrossManyCalls() {
            var gen = Gjuton.of(OBJECT_SCHEMA).withSeed(7L);

            // when
            double previous = gen.valueCoverage();
            for (int i = 0; i < 100; i++) {
                gen.generate();
                double current = gen.valueCoverage();

                // then
                assertThat(current).isGreaterThanOrEqualTo(previous);
                previous = current;
            }
        }

        @Test
        void largeEnumDominatesTheMeasure() {
            var literals = new ArrayList<String>();
            for (int i = 0; i < 100; i++) {
                literals.add("\"v" + i + "\"");
            }
            var gen = Gjuton.of("{ \"enum\": [" + String.join(",", literals) + "] }").withSeed(1L);

            // when: a handful of calls
            for (int i = 0; i < 5; i++) {
                gen.generate();
            }

            // then: coverage grows in fine steps, nowhere near complete
            assertThat(gen.valueCoverage()).isLessThan(0.1);
        }

        @Test
        void loopToTargetTerminatesForObjectWithOptionalField() {
            var gen = Gjuton.of("""
                    {
                      "type": "object",
                      "properties": {
                        "req": { "type": "integer", "minimum": 0, "maximum": 5 },
                        "opt": { "type": "boolean" }
                      },
                      "required": ["req"]
                    }""").withSeed(1L);

            // when
            int calls = 0;
            while (gen.valueCoverage() < 1.0 && calls < 1000) {
                gen.generate();
                calls++;
            }

            // then
            assertThat(gen.valueCoverage()).isEqualTo(1.0);
            assertThat(calls).isLessThan(1000);
        }

        @Test
        void sharedRefDefinitionIsCountedOnce() {
            // "flag" is reached through two properties but is one shared
            // generator. Counting its ten literals twice would leave the
            // denominator forever unfillable, so reaching 1.0 proves it is
            // counted once.
            var gen = Gjuton.of("""
                    {
                      "type": "object",
                      "$defs": {
                        "flag": { "enum": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9] }
                      },
                      "properties": {
                        "a": { "$ref": "#/$defs/flag" },
                        "b": { "$ref": "#/$defs/flag" }
                      },
                      "required": ["a", "b"]
                    }""").withSeed(1L);

            // when
            int calls = 0;
            while (gen.valueCoverage() < 1.0 && calls < 1000) {
                gen.generate();
                calls++;
            }

            // then
            assertThat(gen.valueCoverage()).isEqualTo(1.0);
        }

        @Test
        void loopToTargetTerminatesForRecursiveSchema() {
            var gen = Gjuton.of(RECURSIVE_SCHEMA).withSeed(1L);

            // when
            int calls = 0;
            while (gen.valueCoverage() < 0.95 && calls < 1000) {
                gen.generate();
                calls++;
            }

            // then
            assertThat(gen.valueCoverage()).isGreaterThanOrEqualTo(0.95);
            assertThat(calls).isLessThan(1000);
        }
    }

    /**
     * An enum's deliberate value set is every literal. These tests assert the
     * generator actually emits all of them when the enum is nested behind each
     * kind of construct, checking the generated output rather than the coverage
     * measure. The literals are distinctive so random values of other branches
     * cannot masquerade as them.
     */
    @Nested
    class EnumExhaustiveness {

        private static final String[] ENUM_VALUES = {"enumAlphaXQ7", "enumBravoXQ7", "enumCharlieXQ7"};

        @Test
        void emitsAllEnumValuesBehindOptionalProperty() {
            assertEmitsAllEnumValues("""
                    {
                      "type": "object",
                      "properties": { "p": { "enum": ["enumAlphaXQ7", "enumBravoXQ7", "enumCharlieXQ7"] } }
                    }""");
        }

        @Test
        void emitsAllEnumValuesBehindIfThen() {
            assertEmitsAllEnumValues("""
                    {
                      "if": { "properties": { "kind": { "const": "match" } }, "required": ["kind"] },
                      "then": {
                        "properties": { "e": { "enum": ["enumAlphaXQ7", "enumBravoXQ7", "enumCharlieXQ7"] } },
                        "required": ["kind", "e"]
                      }
                    }""");
        }

        @Test
        void emitsAllEnumValuesBehindElse() {
            assertEmitsAllEnumValues("""
                    {
                      "if": { "properties": { "kind": { "const": "match" } }, "required": ["kind"] },
                      "then": { "type": "object" },
                      "else": {
                        "properties": { "e": { "enum": ["enumAlphaXQ7", "enumBravoXQ7", "enumCharlieXQ7"] } },
                        "required": ["e"]
                      }
                    }""");
        }

        @Test
        void emitsAllEnumValuesInsideArray() {
            assertEmitsAllEnumValues("""
                    {
                      "type": "array",
                      "items": { "enum": ["enumAlphaXQ7", "enumBravoXQ7", "enumCharlieXQ7"] },
                      "minItems": 1
                    }""");
        }

        @Test
        void emitsAllEnumValuesBehindOneOf() {
            assertEmitsAllEnumValues("""
                    {
                      "oneOf": [
                        { "enum": ["enumAlphaXQ7", "enumBravoXQ7", "enumCharlieXQ7"] },
                        { "type": "integer" }
                      ]
                    }""");
        }

        @Test
        void emitsAllEnumValuesBehindAnyOf() {
            assertEmitsAllEnumValues("""
                    {
                      "anyOf": [
                        { "enum": ["enumAlphaXQ7", "enumBravoXQ7", "enumCharlieXQ7"] },
                        { "type": "string" }
                      ]
                    }""");
        }

        @Test
        @Disabled("#121 — the enum branch over-matches the string branch, so every enum "
                + "value fails the exactly-one oneOf rule and is discarded; the values are "
                + "swallowed and never reach output, yet valueCoverage() still reports 1.0")
        void emitsAllEnumValuesBehindOverMatchingOneOf() {
            assertEmitsAllEnumValues("""
                    {
                      "oneOf": [
                        { "enum": ["enumAlphaXQ7", "enumBravoXQ7", "enumCharlieXQ7"] },
                        { "type": "string" }
                      ]
                    }""");
        }

        private void assertEmitsAllEnumValues(String schema) {
            var gen = Gjuton.of(schema).withSeed(1L);

            // when
            var seen = new HashSet<String>();
            for (int i = 0; i < 1000 && seen.size() < ENUM_VALUES.length; i++) {
                var output = gen.generate();
                for (var value : ENUM_VALUES) {
                    if (output.contains("\"" + value + "\"")) {
                        seen.add(value);
                    }
                }
            }

            // then
            assertThat(seen).containsExactlyInAnyOrder(ENUM_VALUES);
        }
    }
}
