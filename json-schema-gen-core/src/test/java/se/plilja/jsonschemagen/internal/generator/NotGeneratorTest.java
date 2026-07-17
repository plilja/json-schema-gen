package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.internal.parser.SchemaParser;

class NotGeneratorTest {

    @Test
    void singleDeliberateValueEmittedAfterFirstCall() {
        // when
        var document = SchemaParser.parse("""
                {"not": {"type": "string"}}
                """);
        var context = new GeneratorContext(document, new Random(42));
        var generator = new NotGenerator(context, document.getRoot());

        // then
        assertThat(generator.totalCount()).isEqualTo(1);
        assertThat(generator.emittedCount()).isEqualTo(0);

        // when
        generator.generate();

        // then
        assertThat(generator.emittedCount()).isEqualTo(1);
    }
}
