package io.github.gjuton.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.SchemaDocument;
import io.github.gjuton.internal.parser.SchemaParser;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

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

    @Test
    void minimalModeWithBothBranchesUnsatisfiableThrowsCleanException() {
        // Both then+parent and else+parent conflict with the parent's own "x" constraint,
        // so the schema is genuinely unsatisfiable regardless of mode.
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {"x": {"const": 5}},
                    "if": {"properties": {"x": {"const": 1}}},
                    "then": {"properties": {"x": {"const": 2}}},
                    "else": {"properties": {"x": {"const": 3}}}
                }
                """);
        var context = new GeneratorContext(document, new Random(42));
        context.incrementGlobalRefDepth();
        context.incrementGlobalRefDepth();
        var generator = new IfThenElseGenerator(context, document.getRoot());

        // when / then -- minimal mode must not surface the internal IllegalStateException
        assertThatThrownBy(generator::generate).isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void bothBranchesAreDeliberateValues() {
        // when
        var document = SchemaParser.parse(STATUS_CONDITIONAL);
        var generator = generatorFor(document);

        // then
        assertThat(generator.totalCount()).isEqualTo(2);
        assertThat(generator.emittedCount()).isEqualTo(0);

        // when: then-branch only
        generator.generate();

        // then: only the then branch counts until the else branch is emitted
        assertThat(generator.emittedCount()).isEqualTo(1);

        // when: else-branch
        generator.generate();

        // then
        assertThat(generator.emittedCount()).isEqualTo(2);

        // when: random phase re-picks a branch without exceeding the set
        generator.generate();

        // then
        assertThat(generator.emittedCount()).isEqualTo(2);
    }

    private static IfThenElseGenerator generatorFor(SchemaDocument document) {
        return new IfThenElseGenerator(
                new GeneratorContext(document, new Random(42)),
                document.getRoot());
    }
}
