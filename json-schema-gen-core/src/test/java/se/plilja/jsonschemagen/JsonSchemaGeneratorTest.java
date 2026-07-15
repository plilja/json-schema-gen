package se.plilja.jsonschemagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.api.GenerationMode;
import se.plilja.jsonschemagen.api.JsonSchemaGenerator;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;

class JsonSchemaGeneratorTest {

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
        var unconfigured = generate(JsonSchemaGenerator.of(OBJECT_SCHEMA).withSeed(42L), 50);
        var explicit = generate(
                JsonSchemaGenerator.of(OBJECT_SCHEMA).withMode(GenerationMode.EXHAUSTIVE).withSeed(42L), 50);

        // then
        assertThat(unconfigured).isEqualTo(explicit);
    }

    @Test
    void exhaustiveModeEmitsBoundaryValuesFirst() {
        // when
        var gen = JsonSchemaGenerator.of(INT_SCHEMA).withSeed(1L);

        // then
        assertThat(gen.generate()).isEqualTo("5");
        assertThat(gen.generate()).isEqualTo("100");
    }

    @Test
    void randomOnlyModeSkipsBoundaryValues() {
        // when
        var gen = JsonSchemaGenerator.of(INT_SCHEMA).withMode(GenerationMode.RANDOM_ONLY).withSeed(1L);
        var values = generate(gen, 100);

        // then
        assertThat(values.get(0)).isNotEqualTo("5");
        assertThat(values).allSatisfy(v -> assertThat(Integer.parseInt(v)).isBetween(5, 100));
    }

    @Test
    void withModeLastCallWins() {
        // when
        var gen = JsonSchemaGenerator.of(INT_SCHEMA)
                .withMode(GenerationMode.RANDOM_ONLY)
                .withMode(GenerationMode.EXHAUSTIVE)
                .withSeed(1L);

        // then
        assertThat(gen.generate()).isEqualTo("5");
    }

    @Test
    void additionalPropertiesOffProducesOnlyDeclaredFields() {
        // when
        var gen = JsonSchemaGenerator.of(OBJECT_SCHEMA).withSeed(3L);
        var fieldNames = allFieldNames(gen, 100);

        // then
        assertThat(fieldNames).containsOnly("a");
    }

    @Test
    void additionalPropertiesOnAddsExtraFields() {
        // when
        var gen = JsonSchemaGenerator.of(OBJECT_SCHEMA).withAdditionalProperties().withSeed(3L);
        var fieldNames = allFieldNames(gen, 100);

        // then
        assertThat(fieldNames).contains("a");
        assertThat(fieldNames).hasSizeGreaterThan(1);
    }

    @Test
    void additionalPropertiesOnHonoursAdditionalPropertiesFalse() {
        // when
        var gen = JsonSchemaGenerator.of(CLOSED_OBJECT_SCHEMA).withAdditionalProperties().withSeed(3L);
        var fieldNames = allFieldNames(gen, 100);

        // then
        assertThat(fieldNames).containsOnly("a");
    }

    @Test
    void deepRecursionLimitsAllowDeeperNestingThanShallow() {
        // when
        int shallow = maxNestingDepth(
                JsonSchemaGenerator.of(RECURSIVE_SCHEMA).withRecursionLimitsShallow().withSeed(9L));
        int deep = maxNestingDepth(
                JsonSchemaGenerator.of(RECURSIVE_SCHEMA).withRecursionLimitsDeep().withSeed(9L));

        // then
        assertThat(deep).isGreaterThan(shallow);
    }

    @Test
    void withModeRejectsNull() {
        // then
        assertThatThrownBy(() -> JsonSchemaGenerator.of(INT_SCHEMA).withMode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withRecursionLimitsRejectsSoftBelowOne() {
        // then
        assertThatThrownBy(() -> JsonSchemaGenerator.of(INT_SCHEMA).withRecursionLimits(0, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withRecursionLimitsRejectsSoftAboveHard() {
        // then
        assertThatThrownBy(() -> JsonSchemaGenerator.of(INT_SCHEMA).withRecursionLimits(5, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> generate(JsonSchemaGenerator gen, int iterations) {
        var values = new ArrayList<String>(iterations);
        for (int i = 0; i < iterations; i++) {
            values.add(gen.generate());
        }
        return values;
    }

    private static List<String> allFieldNames(JsonSchemaGenerator gen, int iterations) {
        var names = new ArrayList<String>();
        for (int i = 0; i < iterations; i++) {
            var node = parse(gen.generate());
            node.fieldNames().forEachRemaining(names::add);
        }
        return names;
    }

    private static int maxNestingDepth(JsonSchemaGenerator gen) {
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
            var gen = JsonSchemaGenerator.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withProducer("$.role", () -> "admin");

            // then
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("admin");
        }

        @Test
        void producerInvokedOnEachGenerate() {
            // when
            var counter = new int[] {0};
            var gen = JsonSchemaGenerator.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withProducer("$.role", () -> "user-" + counter[0]++);

            // then
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("user-0");
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("user-1");
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("user-2");
        }

        @Test
        void producerReturningBeanSerializesAsObject() {
            // when
            var gen = JsonSchemaGenerator.of(NESTED_SCHEMA).withSeed(1L)
                    .withProducer("$.a", () -> new Point(3, 4));

            // then
            var a = parse(gen.generate()).get("a");
            assertThat(a.get("x").asInt()).isEqualTo(3);
            assertThat(a.get("y").asInt()).isEqualTo(4);
        }

        @Test
        void producerOnNestedPathOverridesOnlyThatField() {
            // when
            var gen = JsonSchemaGenerator.of(NESTED_SCHEMA).withSeed(1L)
                    .withProducer("$.a.b", () -> "fixed");

            // then
            assertThat(parse(gen.generate()).get("a").get("b").asText()).isEqualTo("fixed");
        }

        @Test
        void producerOnArrayElementOverridesThatIndex() {
            // when
            var gen = JsonSchemaGenerator.of(ARRAY_SCHEMA).withSeed(1L)
                    .withProducer("$[0]", () -> 999);

            // then
            assertThat(parse(gen.generate()).get(0).asInt()).isEqualTo(999);
        }

        @Test
        void producerAtRootReplacesWholeValue() {
            // when
            var gen = JsonSchemaGenerator.of(TWO_FIELD_SCHEMA).withSeed(1L)
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
            assertThatThrownBy(() -> JsonSchemaGenerator.of(schema).withSeed(1L).generate())
                    .isInstanceOf(UnsatisfiableSchemaException.class);

            // when a producer supplies the value, the subtree is never generated
            var gen = JsonSchemaGenerator.of(schema).withSeed(1L)
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
            assertThatThrownBy(() -> JsonSchemaGenerator.of(schema).withSeed(1L).generate())
                    .isInstanceOf(UnsatisfiableSchemaException.class);

            // when a producer supplies the value, the field is never resolved
            var gen = JsonSchemaGenerator.of(schema).withSeed(1L)
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
            var gen = JsonSchemaGenerator.of(schema).withSeed(1L)
                    .withProducer("$.n", () -> "not-a-number");

            // then the override survives validation and appears verbatim
            assertThat(parse(gen.generate()).get("n").asText()).isEqualTo("not-a-number");
        }

        @Test
        void producerOnUnvisitedPathNeverFires() {
            // when a producer targets a field the schema does not declare
            var fired = new boolean[] {false};
            var gen = JsonSchemaGenerator.of(TWO_FIELD_SCHEMA).withSeed(1L)
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
            var gen = JsonSchemaGenerator.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withProducer("$.role", () -> "first")
                    .withProducer("$.role", () -> "second");

            // then
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("second");
        }

        @Test
        void withProducerRejectsNullArguments() {
            // then
            assertThatThrownBy(() -> JsonSchemaGenerator.of(TWO_FIELD_SCHEMA).withProducer(null, () -> "x"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> JsonSchemaGenerator.of(TWO_FIELD_SCHEMA).withProducer("$.role", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private record Point(int x, int y) {
        }
    }
}
