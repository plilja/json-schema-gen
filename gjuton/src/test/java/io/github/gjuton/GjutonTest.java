package io.github.gjuton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gjuton.api.GenerationMode;
import io.github.gjuton.api.Gjuton;
import io.github.gjuton.errors.JsonBindingException;
import io.github.gjuton.errors.UnsatisfiableSchemaException;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
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
    void unconfiguredMatchesExplicitRandom() {
        // when
        var unconfigured = generate(Gjuton.of(OBJECT_SCHEMA).withSeed(42L), 50);
        var explicit = generate(
                Gjuton.of(OBJECT_SCHEMA).withGenerationMode(GenerationMode.RANDOM).withSeed(42L), 50);

        // then
        assertThat(unconfigured).isEqualTo(explicit);
    }

    @Test
    void exhaustiveModeEmitsBoundaryValuesFirst() {
        // when
        var gen = Gjuton.of(INT_SCHEMA).withGenerationMode(GenerationMode.EXHAUSTIVE).withSeed(1L);

        // then
        assertThat(gen.generate()).isEqualTo("5");
        assertThat(gen.generate()).isEqualTo("100");
    }

    @Test
    void randomModeSkipsBoundaryValues() {
        // when
        var gen = Gjuton.of(INT_SCHEMA).withGenerationMode(GenerationMode.RANDOM).withSeed(1L);
        var values = generate(gen, 100);

        // then
        assertThat(values.get(0)).isNotEqualTo("5");
        assertThat(values).allSatisfy(v -> assertThat(Integer.parseInt(v)).isBetween(5, 100));
    }

    @Test
    void withGenerationModeLastCallWins() {
        // when
        var gen = Gjuton.of(INT_SCHEMA)
                .withGenerationMode(GenerationMode.RANDOM)
                .withGenerationMode(GenerationMode.EXHAUSTIVE)
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
    void withGenerationModeRejectsNull() {
        // then
        assertThatThrownBy(() -> Gjuton.of(INT_SCHEMA).withGenerationMode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factoriesRejectNullSchema() {
        // then
        assertThatThrownBy(() -> Gjuton.of((String) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Gjuton.of((File) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Gjuton.of((InputStream) null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateToTargetRejectsNull() {
        // when
        var gen = Gjuton.of(INT_SCHEMA);

        // then
        assertThatThrownBy(() -> gen.generate((OutputStream) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gen.generate((Writer) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gen.generate((File) null)).isInstanceOf(IllegalArgumentException.class);
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
    class OverridesByPath {

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
        void overrideReplacesFieldValue() {
            // when
            var gen = Gjuton.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withOverrideByPath("$.role", () -> "admin");

            // then
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("admin");
        }

        @Test
        void overrideInvokedOnEachGenerate() {
            // when
            var counter = new int[] {0};
            var gen = Gjuton.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withOverrideByPath("$.role", () -> "user-" + counter[0]++);

            // then
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("user-0");
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("user-1");
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("user-2");
        }

        @Test
        void overrideReturningBeanSerializesAsObject() {
            // when
            var gen = Gjuton.of(NESTED_SCHEMA).withSeed(1L)
                    .withOverrideByPath("$.a", () -> new Point(3, 4));

            // then
            var a = parse(gen.generate()).get("a");
            assertThat(a.get("x").asInt()).isEqualTo(3);
            assertThat(a.get("y").asInt()).isEqualTo(4);
        }

        @Test
        void overrideOnNestedPathOverridesOnlyThatField() {
            // when
            var gen = Gjuton.of(NESTED_SCHEMA).withSeed(1L)
                    .withOverrideByPath("$.a.b", () -> "fixed");

            // then
            assertThat(parse(gen.generate()).get("a").get("b").asText()).isEqualTo("fixed");
        }

        @Test
        void overrideOnArrayElementOverridesThatIndex() {
            // when
            var gen = Gjuton.of(ARRAY_SCHEMA).withSeed(1L)
                    .withOverrideByPath("$[0]", () -> 999);

            // then
            assertThat(parse(gen.generate()).get(0).asInt()).isEqualTo(999);
        }

        @Test
        void overrideAtRootReplacesWholeValue() {
            // when
            var gen = Gjuton.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withOverrideByPath("$", () -> List.of("replaced"));

            // then
            var root = parse(gen.generate());
            assertThat(root.isArray()).isTrue();
            assertThat(root.get(0).asText()).isEqualTo("replaced");
        }

        @Test
        void overrideBypassesGenerationOfUnsatisfiableField() {
            // given a schema whose required field can never be generated
            var schema = """
                    { "type": "object", "properties": { "x": false }, "required": ["x"] }""";

            // then generation fails without an override
            assertThatThrownBy(() -> Gjuton.of(schema).withSeed(1L).generate())
                    .isInstanceOf(UnsatisfiableSchemaException.class);

            // when an override supplies the value, the subtree is never generated
            var gen = Gjuton.of(schema).withSeed(1L)
                    .withOverrideByPath("$.x", () -> "supplied");

            // then
            assertThat(parse(gen.generate()).get("x").asText()).isEqualTo("supplied");
        }

        @Test
        void overrideBypassesRequiredFieldWithNoSchema() {
            // given a required field the schema neither declares nor allows
            var schema = """
                    { "type": "object", "required": ["x"], "additionalProperties": false }""";

            // then generation fails without an override
            assertThatThrownBy(() -> Gjuton.of(schema).withSeed(1L).generate())
                    .isInstanceOf(UnsatisfiableSchemaException.class);

            // when an override supplies the value, the field is never resolved
            var gen = Gjuton.of(schema).withSeed(1L)
                    .withOverrideByPath("$.x", () -> "supplied");

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
                    .withOverrideByPath("$.n", () -> "not-a-number");

            // then the override survives validation and appears verbatim
            assertThat(parse(gen.generate()).get("n").asText()).isEqualTo("not-a-number");
        }

        @Test
        void overrideOnUnvisitedPathNeverFires() {
            // when an override targets a field the schema does not declare
            var fired = new boolean[] {false};
            var gen = Gjuton.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withOverrideByPath("$.absent", () -> {
                        fired[0] = true;
                        return "x";
                    });
            gen.generate();

            // then it is never invoked
            assertThat(fired[0]).isFalse();
        }

        @Test
        void withOverrideByPathLastCallWinsForSamePath() {
            // when
            var gen = Gjuton.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withOverrideByPath("$.role", () -> "first")
                    .withOverrideByPath("$.role", () -> "second");

            // then
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("second");
        }

        @Test
        void withOverrideByPathRejectsNullArguments() {
            // then
            assertThatThrownBy(() -> Gjuton.of(TWO_FIELD_SCHEMA).withOverrideByPath(null, () -> "x"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Gjuton.of(TWO_FIELD_SCHEMA).withOverrideByPath("$.role", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private record Point(int x, int y) {
        }
    }

    @Nested
    class OverridesByName {

        private static final String TWO_FIELD_SCHEMA = """
                {
                  "type": "object",
                  "properties": { "role": { "type": "string" }, "n": { "type": "integer" } },
                  "required": ["role", "n"]
                }""";

        @Test
        void overrideByNameMatchesAtMultiplePositions() {
            // given a schema with the same property name at two different paths
            var schema = """
                    {
                      "type": "object",
                      "properties": {
                        "id": { "type": "string" },
                        "child": {
                          "type": "object",
                          "properties": { "id": { "type": "string" } },
                          "required": ["id"]
                        }
                      },
                      "required": ["id", "child"]
                    }""";

            // when
            var gen = Gjuton.of(schema).withSeed(1L)
                    .withOverrideByName("id", () -> "fixed-id");

            // then
            var root = parse(gen.generate());
            assertThat(root.get("id").asText()).isEqualTo("fixed-id");
            assertThat(root.get("child").get("id").asText()).isEqualTo("fixed-id");
        }

        @Test
        void overrideByNameSharesValueAcrossPositionsWithinOneGenerate() {
            // given a schema with the same property name at two different paths
            var schema = """
                    {
                      "type": "object",
                      "properties": {
                        "id": { "type": "string" },
                        "child": {
                          "type": "object",
                          "properties": { "id": { "type": "string" } },
                          "required": ["id"]
                        }
                      },
                      "required": ["id", "child"]
                    }""";

            // when — a counter proves the override fires once per generate(), not per position
            var counter = new int[] {0};
            var gen = Gjuton.of(schema).withSeed(1L)
                    .withOverrideByName("id", () -> "id-" + counter[0]++);

            // then — both positions share the same value within one generate() call
            var root = parse(gen.generate());
            assertThat(root.get("id").asText()).isEqualTo("id-0");
            assertThat(root.get("child").get("id").asText()).isEqualTo("id-0");

            // and a second generate() call gets a fresh value
            var root2 = parse(gen.generate());
            assertThat(root2.get("id").asText()).isEqualTo("id-1");
            assertThat(root2.get("child").get("id").asText()).isEqualTo("id-1");
        }

        @Test
        void pathOverrideTakesPrecedenceOverNameOverride() {
            // when
            var gen = Gjuton.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withOverrideByName("role", () -> "by-name")
                    .withOverrideByPath("$.role", () -> "by-path");

            // then
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("by-path");
        }

        @Test
        void overrideByNameDoesNotMatchArrayElements() {
            var schema = """
                    { "type": "array", "items": { "type": "integer" }, "minItems": 2 }""";

            // when — register a name that happens to be the string "0"
            var fired = new boolean[] {false};
            var gen = Gjuton.of(schema).withSeed(1L)
                    .withOverrideByName("0", () -> {
                        fired[0] = true;
                        return 999;
                    });
            gen.generate();

            // then
            assertThat(fired[0]).isFalse();
        }

        @Test
        void withOverrideByNameLastCallWinsForSameName() {
            // when
            var gen = Gjuton.of(TWO_FIELD_SCHEMA).withSeed(1L)
                    .withOverrideByName("role", () -> "first")
                    .withOverrideByName("role", () -> "second");

            // then
            assertThat(parse(gen.generate()).get("role").asText()).isEqualTo("second");
        }

        @Test
        void withOverrideByNameRejectsNullArguments() {
            // then
            assertThatThrownBy(() -> Gjuton.of(TWO_FIELD_SCHEMA).withOverrideByName(null, () -> "x"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Gjuton.of(TWO_FIELD_SCHEMA).withOverrideByName("role", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class GenerateIntoType {

        private record Bean(int a) {
        }

        @Test
        void bindsGeneratedValueIntoPojo() {
            // given a schema whose integer field fits the target's int component
            var schema = """
                    {
                      "type": "object",
                      "properties": { "a": { "type": "integer", "minimum": 5, "maximum": 100 } },
                      "required": ["a"]
                    }""";

            // when
            var bean = Gjuton.of(schema).withSeed(1L).generate(Bean.class);

            // then
            assertThat(bean.a()).isBetween(5, 100);
        }

        @Test
        void bindingFailureThrowsJsonBindingException() {
            // given a schema whose generated field type cannot map onto the target
            var schema = """
                    {
                      "type": "object",
                      "properties": { "a": { "type": "array", "items": { "type": "integer" } } },
                      "required": ["a"]
                    }""";

            // then
            assertThatThrownBy(() -> Gjuton.of(schema).withSeed(1L).generate(Bean.class))
                    .isInstanceOf(JsonBindingException.class);
        }

        @Test
        void matchesDeserializingGenerateOutput() {
            // given two generators with the same schema and seed
            var schema = """
                    {
                      "type": "object",
                      "properties": { "a": { "type": "integer", "minimum": 5, "maximum": 100 } },
                      "required": ["a"]
                    }""";
            var stringGen = Gjuton.of(schema).withSeed(1L);
            var typedGen = Gjuton.of(schema).withSeed(1L);

            // when
            var fromString = parse(stringGen.generate());
            var fromTyped = typedGen.generate(Bean.class);

            // then
            assertThat(fromTyped.a()).isEqualTo(fromString.get("a").asInt());
        }
    }

    @Nested
    class NoveltyScore {

        @Test
        void startsAtOneBeforeAnyGeneration() {
            // when
            var gen = Gjuton.of(INT_SCHEMA).withGenerationMode(GenerationMode.EXHAUSTIVE).withSeed(1L);

            // then
            assertThat(gen.noveltyScore()).isEqualTo(1.0);
        }

        @Test
        void doesNotThrowInRandomMode() {
            // when
            var gen = Gjuton.of(INT_SCHEMA).withGenerationMode(GenerationMode.RANDOM).withSeed(1L);
            gen.generate();

            // then
            assertThat(gen.noveltyScore()).isEqualTo(1.0);
        }

        @Test
        void dropsAsTheSameValuesRepeat() {
            var gen = Gjuton.of("""
                    { "enum": ["a", "b", "c"] }""").withGenerationMode(GenerationMode.EXHAUSTIVE).withSeed(1L);

            // when: each of the three literals is novel in turn
            gen.generate();
            gen.generate();
            gen.generate();

            // then
            assertThat(gen.noveltyScore()).isEqualTo(1.0);

            // when: two more calls can only repeat an already-seen literal
            gen.generate();
            gen.generate();

            // then: those two non-novel calls pull the score down
            assertThat(gen.noveltyScore()).isEqualTo(0.6);
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
                + "swallowed and never reach output, yet the enum's own phases still advance "
                + "as if each one had been emitted")
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
            var gen = Gjuton.of(schema).withGenerationMode(GenerationMode.EXHAUSTIVE).withSeed(1L);

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
