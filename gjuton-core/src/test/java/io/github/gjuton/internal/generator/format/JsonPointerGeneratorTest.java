package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class JsonPointerGeneratorTest {

    @Test
    void producesValidJsonPointer() {
        var schema = StringSchema.builder().format(StringFormat.JSON_POINTER).build();
        var generator = new JsonPointerGenerator(withSeed(42), schema);

        // when — skip the EMPTY phase
        generator.generate();
        var result = (String) generator.generate();

        // then — a non-empty JSON pointer starts with '/' and each segment is '/'-prefixed
        assertThat(result).startsWith("/");
        assertThat(result.substring(1).split("/")).allSatisfy(token ->
                assertThat(token).isNotEmpty());
    }

    @Test
    void emitsEmptyPointerFirst() {
        var schema = StringSchema.builder().format(StringFormat.JSON_POINTER).build();
        var generator = new JsonPointerGenerator(withSeed(42), schema);

        // when
        var result = (String) generator.generate();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void producesVariedValues() {
        var schema = StringSchema.builder().format(StringFormat.JSON_POINTER).build();
        var generator = new JsonPointerGenerator(withSeed(42), schema);

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
        // Pattern requiring at least two segments — ~half of random pointers have 1 segment,
        // so without the retry loop many generations would violate the pattern.
        var schema = StringSchema.builder().format(StringFormat.JSON_POINTER).pattern("/.+/.+").build();
        var generator = new JsonPointerGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> (String) generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s).matches("/.+/.+"));
    }

    @Test
    void respectsMinLength() {
        var schema = StringSchema.builder().format(StringFormat.JSON_POINTER).minLength(5).build();
        var generator = new JsonPointerGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> (String) generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isGreaterThanOrEqualTo(5));
    }

    @Test
    void respectsMaxLength() {
        var schema = StringSchema.builder().format(StringFormat.JSON_POINTER).maxLength(5).build();
        var generator = new JsonPointerGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> (String) generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isLessThanOrEqualTo(5));
    }

    @Test
    void occasionallyEmitsEscapeSequences() {
        var schema = StringSchema.builder().format(StringFormat.JSON_POINTER).build();
        var generator = new JsonPointerGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 100)
                .mapToObj(i -> (String) generator.generate())
                .toList();

        // then
        assertThat(results).anySatisfy(s -> assertThat(s).contains("~0"));
        assertThat(results).anySatisfy(s -> assertThat(s).contains("~1"));
    }

    @Test
    void minLengthGreaterThanMaxLengthThrows() {
        var schema = StringSchema.builder().format(StringFormat.JSON_POINTER).minLength(10).maxLength(5).build();

        // when / then
        assertThatThrownBy(() -> new JsonPointerGenerator(withSeed(42), schema))
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }
}
