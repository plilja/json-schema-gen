package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.internal.model.SchemaDocument;
import se.plilja.jsonschemagen.internal.parser.SchemaParser;

class IfThenElseGeneratorTest {

    private static final String STATUS_CONDITIONAL = """
            {
                "type": "object",
                "required": ["status"],
                "properties": {"status": {"enum": ["ok", "error"]}},
                "if": {"properties": {"status": {"const": "ok"}}},
                "then": {"properties": {"data": {"type": "string"}}, "required": ["data"]},
                "else": {"properties": {"message": {"type": "string"}}, "required": ["message"]}
            }
            """;

    @Test
    void everyGeneratedValueSatisfiesTheSchema() {
        var document = SchemaParser.parse(STATUS_CONDITIONAL);
        var validator = new SchemaValidator(new GeneratorContext(document, new Random(42)));
        var generator = generatorFor(document);

        // when
        var values = Stream.generate(generator::generate).limit(50).toList();

        // then
        assertThat(values).allMatch(v -> validator.satisfies(v, document.getRoot()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bothBranchesAreExercised() {
        var document = SchemaParser.parse(STATUS_CONDITIONAL);
        var generator = generatorFor(document);

        // when
        var values = Stream.generate(generator::generate)
                .limit(50)
                .map(v -> (Map<String, Object>) v)
                .toList();

        // then -- then branch (status "ok" + data) and else branch (status "error" + message) both appear
        assertThat(values).anyMatch(m -> "ok".equals(m.get("status")) && m.containsKey("data"));
        assertThat(values).anyMatch(m -> "error".equals(m.get("status")) && m.containsKey("message"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void thenBranchIsHonouredOnFirstGeneration() {
        var document = SchemaParser.parse(STATUS_CONDITIONAL);
        var generator = generatorFor(document);

        // when -- the exhaustive phase honours the then branch first
        var first = (Map<String, Object>) generator.generate();

        // then
        assertThat(first.get("status")).isEqualTo("ok");
        assertThat(first).containsKey("data");
    }

    @Test
    void missingElseLeavesTheFailingBranchUnconstrained() {
        // if + then only: a value that fails if is valid with no extra keys.
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "required": ["status"],
                    "properties": {"status": {"enum": ["ok", "error"]}},
                    "if": {"properties": {"status": {"const": "ok"}}},
                    "then": {"properties": {"data": {"type": "string"}}, "required": ["data"]}
                }
                """);
        var validator = new SchemaValidator(new GeneratorContext(document, new Random(7)));
        var generator = generatorFor(document);

        // when
        var values = Stream.generate(generator::generate).limit(30).toList();

        // then
        assertThat(values).allMatch(v -> validator.satisfies(v, document.getRoot()));
    }

    @Test
    void missingThenLeavesTheMatchingBranchUnconstrained() {
        // if + else only.
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "required": ["status"],
                    "properties": {"status": {"enum": ["ok", "error"]}},
                    "if": {"properties": {"status": {"const": "ok"}}},
                    "else": {"properties": {"message": {"type": "string"}}, "required": ["message"]}
                }
                """);
        var validator = new SchemaValidator(new GeneratorContext(document, new Random(7)));
        var generator = generatorFor(document);

        // when
        var values = Stream.generate(generator::generate).limit(30).toList();

        // then
        assertThat(values).allMatch(v -> validator.satisfies(v, document.getRoot()));
    }

    @Test
    void conditionalInsideAllOfBranchIsHonoured() {
        // The conditional lives inside an allOf branch, not at the top level,
        // so it reaches the generator only if SchemaMerger carries it through.
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "required": ["status"],
                    "properties": {"status": {"enum": ["ok", "error"]}},
                    "allOf": [
                        {
                            "if": {"properties": {"status": {"const": "ok"}}},
                            "then": {"properties": {"data": {"type": "string"}}, "required": ["data"]},
                            "else": {"properties": {"message": {"type": "string"}}, "required": ["message"]}
                        }
                    ]
                }
                """);
        var context = new GeneratorContext(document, new Random(42));
        var validator = new SchemaValidator(context);
        var generator = new JsonGenerator(document.getRoot(), context);

        // when
        var values = Stream.generate(generator::generate).limit(50).toList();

        // then
        assertThat(values).allMatch(v -> validator.satisfies(v, document.getRoot()));
    }

    @Test
    void dispatchedViaJsonGenerator() {
        // Proves buildDelegate routes an if/then/else schema to this generator.
        var document = SchemaParser.parse(STATUS_CONDITIONAL);
        var context = new GeneratorContext(document, new Random(42));
        var validator = new SchemaValidator(context);
        var generator = new JsonGenerator(document.getRoot(), context);

        // when
        var values = Stream.generate(generator::generate).limit(20).toList();

        // then
        assertThat(values).allMatch(v -> validator.satisfies(v, document.getRoot()));
    }

    private static IfThenElseGenerator generatorFor(SchemaDocument document) {
        return new IfThenElseGenerator(
                new GeneratorContext(document, new Random(42)),
                document.getRoot());
    }
}
