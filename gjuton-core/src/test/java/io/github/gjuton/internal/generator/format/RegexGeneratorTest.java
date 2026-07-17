package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RegexGeneratorTest {

    @Test
    void producesValidRegex() {
        var schema = StringSchema.builder().format(StringFormat.REGEX).build();
        var generator = new RegexGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> (String) generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s ->
                assertThat(s).satisfies(regex -> Pattern.compile(regex)));
    }

    @Test
    void emitsShortRegexFirst() {
        var schema = StringSchema.builder().format(StringFormat.REGEX).build();
        var generator = new RegexGenerator(withSeed(42), schema);

        // when
        var result = (String) generator.generate();

        // then
        assertThat(result).isEqualTo(".");
    }

    @Test
    void shortPhaseRespectsMinLength() {
        var schema = StringSchema.builder().format(StringFormat.REGEX).minLength(5).build();
        var generator = new RegexGenerator(withSeed(42), schema);

        // when
        var result = (String) generator.generate();

        // then
        assertThat(result).isEqualTo(".....");
    }

    @Test
    void shortPhaseEmitsEmptyStringWhenMinLengthIsZero() {
        var schema = StringSchema.builder().format(StringFormat.REGEX).minLength(0).build();
        var generator = new RegexGenerator(withSeed(42), schema);

        // when
        var result = (String) generator.generate();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void emitsLongRegexSecond() {
        var schema = StringSchema.builder().format(StringFormat.REGEX).build();
        var generator = new RegexGenerator(withSeed(42), schema);

        // when
        generator.generate();
        var result = (String) generator.generate();

        // then
        assertThat(result).isEqualTo(".".repeat(30));
    }

    @Test
    void producesVariedValues() {
        var schema = StringSchema.builder().format(StringFormat.REGEX).build();
        var generator = new RegexGenerator(withSeed(42), schema);

        // when
        var distinct = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .distinct()
                .count();

        // then
        assertThat(distinct).isGreaterThan(1);
    }

    @Test
    void respectsMaxLength() {
        var schema = StringSchema.builder().format(StringFormat.REGEX).maxLength(5).build();
        var generator = new RegexGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> (String) generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isLessThanOrEqualTo(5));
    }

    @Test
    void respectsMinLength() {
        var schema = StringSchema.builder().format(StringFormat.REGEX).minLength(10).build();
        var generator = new RegexGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> (String) generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isGreaterThanOrEqualTo(10));
    }

    @Test
    void minLengthGreaterThanMaxLengthThrows() {
        var schema = StringSchema.builder().format(StringFormat.REGEX).minLength(10).maxLength(5).build();

        // when / then
        assertThatThrownBy(() -> new RegexGenerator(withSeed(42), schema))
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }
}
