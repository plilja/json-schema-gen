package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IriReferenceGeneratorTest {

    @Test
    void singleDeliberateValueEmittedAfterFirstCall() {
        // when
        var schema = StringSchema.builder().format(StringFormat.IRI_REFERENCE).build();
        var generator = new IriReferenceGenerator(withSeed(42), schema);

        // then
        assertThat(generator.totalCount()).isEqualTo(1);
        assertThat(generator.emittedCount()).isEqualTo(0);

        // when
        generator.generate();

        // then
        assertThat(generator.emittedCount()).isEqualTo(1);
    }

    @Test
    void firstCallReturnsEmptyString() {
        var schema = StringSchema.builder().format(StringFormat.IRI_REFERENCE).build();
        var generator = new IriReferenceGenerator(withSeed(42), schema);

        // when
        var result = generator.generate();

        // then
        assertThat(result).isEqualTo("");
    }

    @Test
    void respectsTightUpperBound() {
        var schema = StringSchema.builder().format(StringFormat.IRI_REFERENCE).maxLength(20).build();
        var generator = new IriReferenceGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isLessThanOrEqualTo(20));
    }

    @Test
    void respectsMinLength() {
        var schema = StringSchema.builder().format(StringFormat.IRI_REFERENCE).minLength(5).build();
        var generator = new IriReferenceGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isGreaterThanOrEqualTo(5));
    }

    @Test
    void unsatisfiableMinLengthThrows() {
        var schema = StringSchema.builder().format(StringFormat.IRI_REFERENCE).minLength(5000).build();

        // when / then
        assertThatThrownBy(() -> new IriReferenceGenerator(withSeed(42), schema))
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void emitsAtLeastOneValueWithUnicodeCharacters() {
        var schema = StringSchema.builder().format(StringFormat.IRI_REFERENCE).build();
        var generator = new IriReferenceGenerator(withSeed(42), schema);

        // when
        var anyHasNonAscii = IntStream.range(0, 200)
                .mapToObj(i -> generator.generate())
                .anyMatch(s -> s.codePoints().anyMatch(cp -> cp > 0x7F));

        // then
        assertThat(anyHasNonAscii).isTrue();
    }
}
