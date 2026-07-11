package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static se.plilja.jsonschemagen.internal.generator.TestContexts.withSeed;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.model.UntypedSchema;
import se.plilja.jsonschemagen.internal.parser.SchemaParser;

class EnumGeneratorTest {

    private static Schema enumSchema(List<Object> values) {
        return UntypedSchema.builder().enumValues(values).build();
    }

    @Test
    void exhaustsAllValuesInOrder() {
        var values = List.<Object>of("red", "green", "blue");
        var generator = new EnumGenerator(withSeed(42), values, enumSchema(values));

        // when
        var first = generator.generate();
        var second = generator.generate();
        var third = generator.generate();

        // then
        assertThat(first).isEqualTo("red");
        assertThat(second).isEqualTo("green");
        assertThat(third).isEqualTo("blue");
    }

    @Test
    void exhaustsMixedTypeValuesInOrder() {
        var values = Arrays.<Object>asList("admin", 1, true, null);
        var generator = new EnumGenerator(withSeed(42), values, enumSchema(values));

        // when
        var first = generator.generate();
        var second = generator.generate();
        var third = generator.generate();
        var fourth = generator.generate();

        // then
        assertThat(first).isEqualTo("admin");
        assertThat(second).isEqualTo(1);
        assertThat(third).isEqualTo(true);
        assertThat(fourth).isNull();
    }

    @Test
    void producesRandomValuesAfterExhausting() {
        var values = List.<Object>of("red", "green", "blue");
        var generator = new EnumGenerator(withSeed(42), values, enumSchema(values));
        // exhaust all values
        generator.generate();
        generator.generate();
        generator.generate();

        // when
        var fourth = generator.generate();
        var fifth = generator.generate();
        var sixth = generator.generate();

        // then
        assertThat(fourth).isEqualTo("blue");
        assertThat(fifth).isEqualTo("red");
        assertThat(sixth).isEqualTo("red");
    }

    @Test
    void throwsWhenNoEnumValueSatisfiesCombiningKeyword() {
        var root = SchemaParser.parse("""
                {
                    "type": "integer",
                    "enum": [10, 20],
                    "allOf": [ { "minimum": 100 } ]
                }
                """).getRoot();

        // when / then
        assertThatThrownBy(() -> new EnumGenerator(withSeed(42), root.getEnumValues(), root))
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void sharedInstanceInMinimalModeDoesNotThrowWhenCalledMoreTimesThanValues() {
        // A single EnumGenerator instance is shared (via GeneratorContext's identity
        // cache) across every call site reaching the same $ref'd schema. In minimal
        // mode, generate() always retries from minimalPhase() rather than persisting
        // the shared phase field, so a heavily-referenced enum can be asked for more
        // values than it has.
        var values = List.<Object>of("red", "green");
        var context = withSeed(42);
        context.incrementGlobalRefDepth();
        context.incrementGlobalRefDepth();
        var generator = new EnumGenerator(context, values, enumSchema(values));

        // when / then
        for (int i = 0; i < 5; i++) {
            assertThatCode(generator::generate).doesNotThrowAnyException();
        }
    }
}
