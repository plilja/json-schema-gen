package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UuidGeneratorTest {

    @Test
    void producesCanonicalUuid() {
        var schema = StringSchema.builder().format(StringFormat.UUID).build();
        var generator = new UuidGenerator(withSeed(42), schema);

        // when
        var result = generator.generate();

        // then
        assertThatNoException().isThrownBy(() -> UUID.fromString(result));
        assertThat(result).hasSize(36);
    }

    @Test
    void producesVariedValues() {
        var schema = StringSchema.builder().format(StringFormat.UUID).build();
        var generator = new UuidGenerator(withSeed(42), schema);

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
        // Synthetic pattern chosen for selectivity, not realism: ~1/16 random UUIDs start with 'a',
        // so without the retry loop most generations would violate it. Realistic UUID-with-pattern
        // schemas (e.g. v4 enforcement) are covered end-to-end in IntegrationTest.
        var schema = StringSchema.builder().format(StringFormat.UUID).pattern("^a").build();
        var generator = new UuidGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThatNoException().isThrownBy(() -> UUID.fromString(s));
            assertThat(s).startsWith("a");
        });
    }

    @Test
    void unsatisfiableLengthThrows() {
        // Canonical UUIDs are always 36 chars; a minLength of 40 cannot be satisfied.
        var schema = StringSchema.builder().format(StringFormat.UUID).minLength(40).build();
        var generator = new UuidGenerator(withSeed(42), schema);

        // when / then
        assertThatThrownBy(generator::generate).isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void unsatisfiableMaxLengthThrows() {
        // Canonical UUIDs are always 36 chars; a maxLength of 20 cannot be satisfied.
        var schema = StringSchema.builder().format(StringFormat.UUID).maxLength(20).build();
        var generator = new UuidGenerator(withSeed(42), schema);

        // when / then
        assertThatThrownBy(generator::generate).isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void respectsExactBoundaryLength() {
        // minLength == maxLength == 36 is satisfiable (the canonical UUID length).
        var schema = StringSchema.builder().format(StringFormat.UUID).minLength(36).maxLength(36).build();
        var generator = new UuidGenerator(withSeed(42), schema);

        // when
        var result = generator.generate();

        // then
        assertThatNoException().isThrownBy(() -> UUID.fromString(result));
        assertThat(result).hasSize(36);
    }
}
