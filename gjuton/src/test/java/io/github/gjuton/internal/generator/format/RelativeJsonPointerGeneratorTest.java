package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RelativeJsonPointerGeneratorTest {

    @Test
    void producesValidRelativeJsonPointer() {
        var schema = StringSchema.builder().format(StringFormat.RELATIVE_JSON_POINTER).build();
        var generator = new RelativeJsonPointerGenerator(withSeed(42), schema);

        // when — skip the SELF phase
        generator.generate();
        var result = (String) generator.generate();

        // then — starts with a digit, followed by either '#' or a JSON pointer
        assertThat(result).matches("\\d+(#|(/[^/]+)*)");
    }

    @Test
    void emitsSelfReferenceFirst() {
        var schema = StringSchema.builder().format(StringFormat.RELATIVE_JSON_POINTER).build();
        var generator = new RelativeJsonPointerGenerator(withSeed(42), schema);

        // when
        var result = (String) generator.generate();

        // then
        assertThat(result).isEqualTo("0");
    }

    @Test
    void producesVariedValues() {
        var schema = StringSchema.builder().format(StringFormat.RELATIVE_JSON_POINTER).build();
        var generator = new RelativeJsonPointerGenerator(withSeed(42), schema);

        // when
        var distinct = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .distinct()
                .count();

        // then
        assertThat(distinct).isGreaterThan(1);
    }

    @Test
    void filtersCandidatesByPattern() {
        // Pattern requiring the '#' suffix — ~half of random values use '#', so without the
        // retry loop many generations would violate it.
        var schema = StringSchema.builder().format(StringFormat.RELATIVE_JSON_POINTER).pattern("#$").build();
        var generator = new RelativeJsonPointerGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> (String) generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s).endsWith("#"));
    }

    @Test
    void unsatisfiableMaxLengthThrows() {
        var schema = StringSchema.builder().format(StringFormat.RELATIVE_JSON_POINTER).maxLength(0).build();

        // when / then
        assertThatThrownBy(() -> new RelativeJsonPointerGenerator(withSeed(42), schema))
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }
}
