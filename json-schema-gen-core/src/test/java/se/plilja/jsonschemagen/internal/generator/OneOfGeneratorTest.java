package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.internal.parser.SchemaParser;

class OneOfGeneratorTest {

    @Test
    void producesRandomValuesAfterExhaustingBranches() {
        var generator = oneOfGenerator("""
                {
                    "oneOf": [
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
                .limit(5)
                .map(Object::getClass)
                .toList();

        // then
        assertThat(observed).containsExactly(
                Long.class, String.class, Long.class, Long.class, String.class);
    }

    @Test
    void exhaustsSubSchemasInOrderAcrossCalls() {
        var generator = oneOfGenerator("""
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
    void respectsParentSiblingConstraints() {
        var generator = oneOfGenerator("""
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

        // then — parent's required constraint must hold on every value
        assertThat(values).allMatch(m -> m.containsKey("x"));
    }

    private static OneOfGenerator oneOfGenerator(String json) {
        var document = SchemaParser.parse(json);
        return new OneOfGenerator(
                new GeneratorContext(document, new Random(42)),
                document.getRoot());
    }
}
