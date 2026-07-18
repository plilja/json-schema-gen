package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UriReferenceGeneratorTest {

    @Test
    void randomRelativePathHasExactLength() {
        var random = new Random(42);

        // when / then
        for (int length = 0; length <= 50; length++) {
            var path = UriReferenceGenerator.randomRelativePath(length, Alphabets.EN, random);
            assertThat(path.length()).as("length=%d path=%s", length, path).isEqualTo(length);
        }
    }

    @Test
    void randomLongUriHonorsMinLengthWhenMaxLengthIsUnset() {
        var schema = StringSchema.builder().format(StringFormat.URI).minLength(200).build();
        var random = new Random(42);

        // when
        var result = UriReferenceGenerator.randomLongUri(schema, Alphabets.EN, random);

        // then
        assertThat(result.length()).isGreaterThanOrEqualTo(200);
    }

    @Test
    void randomAbsoluteUriOfLengthHasExactTargetLength() {
        var random = new Random(42);

        // when / then
        for (int target = 12; target <= 100; target++) {
            var uri = UriReferenceGenerator.randomAbsoluteUriOfLength("http", target, random);
            assertThat(uri.length()).as("target=%d uri=%s", target, uri).isEqualTo(target);
        }
    }

    @Test
    void randomRelativePathContainsOnlyAlphaAndSlashes() {
        var random = new Random(42);

        // when / then
        for (int length = 1; length <= 30; length++) {
            var path = UriReferenceGenerator.randomRelativePath(length, Alphabets.EN, random);
            assertThat(path).matches("[a-z]+(/[a-z]+)*");
        }
    }

    @Test
    void firstCallReturnsEmptyString() {
        var schema = StringSchema.builder().format(StringFormat.URI_REFERENCE).build();
        var generator = new UriReferenceGenerator(withSeed(42), schema);

        // when
        var result = generator.generate();

        // then
        assertThat(result).isEqualTo("");
    }

    @Test
    void emitsBothRelativeAndAbsoluteFormsAcrossManyCalls() {
        var schema = StringSchema.builder().format(StringFormat.URI_REFERENCE).build();
        var generator = new UriReferenceGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 200)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        var hasAbsolute = results.stream().anyMatch(s -> s.startsWith("http://") || s.startsWith("https://"));
        var hasRelative = results.stream().anyMatch(s -> !s.isEmpty() && !s.contains("://"));
        assertThat(hasAbsolute).as("should emit at least one absolute URI").isTrue();
        assertThat(hasRelative).as("should emit at least one relative reference").isTrue();
    }

    @Test
    void emptyIsSkippedWhenMinLengthIsPositive() {
        var schema = StringSchema.builder().format(StringFormat.URI_REFERENCE).minLength(5).build();
        var generator = new UriReferenceGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).noneMatch(String::isEmpty);
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isGreaterThanOrEqualTo(5));
    }

    @Test
    void respectsTightUpperBound() {
        var schema = StringSchema.builder().format(StringFormat.URI_REFERENCE).maxLength(20).build();
        var generator = new UriReferenceGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isLessThanOrEqualTo(20));
    }

    @Test
    void unsatisfiableMinLengthThrows() {
        var schema = StringSchema.builder().format(StringFormat.URI_REFERENCE).minLength(5000).build();

        // when / then
        assertThatThrownBy(() -> new UriReferenceGenerator(withSeed(42), schema))
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void worksWithMaxLengthBelowMinimumRandomAbsoluteUriOfLength() {
        // shortest absolute URI is 11 chars; with maxLength 5 only relative refs and empty fit.
        var schema = StringSchema.builder().format(StringFormat.URI_REFERENCE).maxLength(5).build();
        var generator = new UriReferenceGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isLessThanOrEqualTo(5));
        assertThat(results).noneMatch(s -> s.contains("://"));
    }
}
