package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IriGeneratorTest {

    @Test
    void singleDeliberateValueEmittedAfterFirstCall() {
        // when
        var schema = StringSchema.builder().format(StringFormat.IRI).build();
        var generator = new IriGenerator(withSeed(42), schema);

        // then
        assertThat(generator.totalCount()).isEqualTo(1);
        assertThat(generator.emittedCount()).isEqualTo(0);

        // when
        generator.generate();

        // then
        assertThat(generator.emittedCount()).isEqualTo(1);
    }

    @Test
    void firstCallReturnsShortAbsoluteIri() {
        var schema = StringSchema.builder().format(StringFormat.IRI).build();
        var generator = new IriGenerator(withSeed(42), schema);

        // when
        var result = generator.generate();

        // then
        assertThat(result).startsWith("http://");
        assertThat(result.length()).isGreaterThanOrEqualTo(11);
    }

    @Test
    void respectsTightUpperBound() {
        var schema = StringSchema.builder().format(StringFormat.IRI).maxLength(30).build();
        var generator = new IriGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isLessThanOrEqualTo(30));
    }

    @Test
    void respectsTightLowerBound() {
        var schema = StringSchema.builder().format(StringFormat.IRI).minLength(50).build();
        var generator = new IriGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isGreaterThanOrEqualTo(50));
    }

    @Test
    void unsatisfiableMaxLengthThrows() {
        var schema = StringSchema.builder().format(StringFormat.IRI).maxLength(10).build();

        // when / then
        assertThatThrownBy(() -> new IriGenerator(withSeed(42), schema))
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void unsatisfiableMinLengthThrows() {
        var schema = StringSchema.builder().format(StringFormat.IRI).minLength(5000).build();

        // when / then
        assertThatThrownBy(() -> new IriGenerator(withSeed(42), schema))
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void emitsAtLeastOneValueWithUnicodeCharacters() {
        var schema = StringSchema.builder().format(StringFormat.IRI).build();
        var generator = new IriGenerator(withSeed(42), schema);

        // when
        var anyHasNonAscii = IntStream.range(0, 200)
                .mapToObj(i -> generator.generate())
                .anyMatch(s -> s.codePoints().anyMatch(cp -> cp > 0x7F));

        // then
        assertThat(anyHasNonAscii).isTrue();
    }
}
