package io.github.gjuton.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gjuton.internal.model.SchemaDocument;
import io.github.gjuton.internal.parser.SchemaParser;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class NotGeneratorTest {

    private static final String NOT_NULL = """
            {
                "not": { "type": "null" }
            }
            """;

    @Test
    void firstCallRegistersAsNovel() {
        var document = SchemaParser.parse(NOT_NULL);
        var context = new GeneratorContext(document, new Random(42));
        var generator = new NotGenerator(context, document.getRoot());

        // when
        context.startRun();
        generator.generate();
        context.completeRun();

        // then
        assertThat(context.noveltyScore()).isEqualTo(1.0);
    }

    @Test
    void secondCallIsNotNovel() {
        var document = SchemaParser.parse(NOT_NULL);
        var context = new GeneratorContext(document, new Random(42));
        var generator = new NotGenerator(context, document.getRoot());

        // when
        context.startRun();
        generator.generate();
        context.completeRun();
        context.startRun();
        generator.generate();
        context.completeRun();

        // then
        assertThat(context.noveltyScore()).isEqualTo(0.5);
    }
}
