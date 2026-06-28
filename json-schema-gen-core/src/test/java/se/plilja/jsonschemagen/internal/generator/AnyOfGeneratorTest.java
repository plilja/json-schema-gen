package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.internal.parser.SchemaParser;

class AnyOfGeneratorTest {

    @Test
    void producesValuesFromBranchesAfterExhaustion() {
        var generator = anyOfGenerator("""
                {
                    "anyOf": [
                        {"type": "string"},
                        {"type": "integer"}
                    ]
                }
                """);
        // exhaust both branches
        generator.generate();
        generator.generate();

        // when
        var observed = Stream.generate(generator::generate)
                .limit(20)
                .toList();

        // then — every value matches one of the branch types
        assertThat(observed).allMatch(v -> v instanceof String || v instanceof Number);
    }

    @Test
    void exhaustsSubSchemasInOrderAcrossCalls() {
        var generator = anyOfGenerator("""
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
        var generator = anyOfGenerator("""
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
        // exhaust both branches
        generator.generate();
        generator.generate();

        // when
        var values = Stream.generate(generator::generate)
                .limit(50)
                .map(v -> (Map<?, ?>) v)
                .toList();

        // then — at least one merged result contains both keys
        assertThat(values).anyMatch(m -> m.containsKey("foo") && m.containsKey("bar"));
    }

    @Test
    void fallsBackToSmallerSubsetWhenMergeIsUnsatisfiable() {
        // Branches are pairwise unmergeable. Phase 2 must drop N until merge succeeds
        // (N=1 always does).
        var generator = anyOfGenerator("""
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

        // then — every call produces a value of one of the branch types
        assertThat(values).hasSize(50);
        assertThat(values).allMatch(v -> v instanceof String || v instanceof Number);
    }

    @Test
    void respectsParentSiblingConstraints() {
        var generator = anyOfGenerator("""
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
                .map(v -> (Map<?, ?>) v)
                .toList();

        // then — parent's required constraint must hold on every value
        assertThat(values).allMatch(m -> m.containsKey("x"));
    }

    private static AnyOfAllOfOneOfGenerator anyOfGenerator(String json) {
        var document = SchemaParser.parse(json);
        return new AnyOfAllOfOneOfGenerator(
                new GeneratorContext(document, new Random(42)),
                document.getRoot());
    }
}
