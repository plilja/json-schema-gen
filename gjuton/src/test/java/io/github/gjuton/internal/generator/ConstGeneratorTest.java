package io.github.gjuton.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.Schema;
import io.github.gjuton.internal.model.SchemaDocument;
import io.github.gjuton.internal.parser.SchemaParser;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ConstGeneratorTest {

    private static final String CONST_SATISFYING_ONE_OF = """
            {
                "type": "integer",
                "const": 5,
                "oneOf": [ { "maximum": 10 }, { "minimum": 100 } ]
            }
            """;

    private static final String CONST_VIOLATING_ONE_OF = """
            {
                "type": "integer",
                "const": 50,
                "oneOf": [ { "maximum": 10 }, { "minimum": 100 } ]
            }
            """;

    @Test
    void emitsConstWhenItSatisfiesCombiningKeyword() {
        var root = SchemaParser.parse(CONST_SATISFYING_ONE_OF).getRoot();
        var generator = new ConstGenerator(contextFor(root), root.getConstValue(), root);

        // when
        var value = generator.generate();

        // then
        assertThat(value).isEqualTo(5);
    }

    @Test
    void throwsWhenConstViolatesCombiningKeyword() {
        var root = SchemaParser.parse(CONST_VIOLATING_ONE_OF).getRoot();

        // when / then
        assertThatThrownBy(() -> new ConstGenerator(contextFor(root), root.getConstValue(), root))
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void singleDeliberateValueEmittedAfterFirstCall() {
        // when
        var root = SchemaParser.parse(CONST_SATISFYING_ONE_OF).getRoot();
        var generator = new ConstGenerator(contextFor(root), root.getConstValue(), root);

        // then
        assertThat(generator.totalCount()).isEqualTo(1);
        assertThat(generator.emittedCount()).isEqualTo(0);

        // when
        generator.generate();

        // then
        assertThat(generator.emittedCount()).isEqualTo(1);
    }

    private static GeneratorContext contextFor(Schema root) {
        var document = new SchemaDocument(root, Map.of());
        return new GeneratorContext(document, new Random(42));
    }
}
