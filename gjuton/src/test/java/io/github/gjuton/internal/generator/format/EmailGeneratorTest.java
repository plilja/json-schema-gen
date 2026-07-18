package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EmailGeneratorTest {

    @Test
    void producesValueContainingAt() {
        var schema = StringSchema.builder().format(StringFormat.EMAIL).build();
        var generator = new EmailGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s).contains("@"));
    }

    @Test
    void producesVariedValues() {
        var schema = StringSchema.builder().format(StringFormat.EMAIL).build();
        var generator = new EmailGenerator(withSeed(42), schema);

        // when
        var distinct = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .distinct()
                .count();

        // then
        assertThat(distinct).isGreaterThan(1);
    }

    @Test
    void composesWithPattern() {
        var schema = StringSchema.builder().format(StringFormat.EMAIL).pattern("@[a-z]+\\.com$").build();
        var generator = new EmailGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThat(s).contains("@");
            assertThat(s).matches(".*@[a-z]+\\.com$");
        });
    }

    @Test
    void firstResultMatchesMinLengthExactly() {
        // Even when minLength > 6, the SHORT phase should emit a candidate
        // at exactly minLength rather than skipping straight to random.
        var schema = StringSchema.builder().format(StringFormat.EMAIL).minLength(18).build();
        var generator = new EmailGenerator(withSeed(42), schema);

        // when
        var first = generator.generate();

        // then
        assertThat(first).contains("@");
        assertThat(first).hasSize(18);
    }

    @Test
    void secondResultMatchesMaxLengthExactly() {
        var schema = StringSchema.builder().format(StringFormat.EMAIL).maxLength(30).build();
        var generator = new EmailGenerator(withSeed(42), schema);

        // when
        generator.generate();
        var second = generator.generate();

        // then
        assertThat(second).contains("@");
        assertThat(second).hasSize(30);
    }

    @Test
    void exactBoundaryLengthAcceptsCanonical() {
        // minLength == maxLength == 6: only "a@a.co" can satisfy the SHORT phase.
        // Kills the `<` -> `<=` and `>` -> `>=` mutations in acceptable().
        var schema = StringSchema.builder().format(StringFormat.EMAIL).minLength(6).maxLength(6).build();
        var generator = new EmailGenerator(withSeed(42), schema);

        // when
        var first = generator.generate();

        // then
        assertThat(first).isEqualTo("a@a.co");
    }

    @Test
    void tightMaxLengthFiltersTooLongCandidates() {
        // maxLength=10 is below the typical random-email length, forcing
        // acceptable() to exercise the maxLength `return false` branch.
        var schema = StringSchema.builder().format(StringFormat.EMAIL).maxLength(10).build();
        var generator = new EmailGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThat(s).contains("@");
            assertThat(s.length()).isLessThanOrEqualTo(10);
        });
    }

    @Test
    void acceptsMinLengthBeyondRandomPhaseCeiling() {
        // The LONG phase can grow the local part up to LOCAL_LEN_RFC_CAP (64) chars,
        // so a schema with minLength well beyond the random-phase ceiling is satisfiable.
        var schema = StringSchema.builder().format(StringFormat.EMAIL).minLength(40).maxLength(60).build();
        var generator = new EmailGenerator(withSeed(42), schema);

        // when
        var first = generator.generate();

        // then
        assertThat(first).contains("@");
        assertThat(first.length()).isBetween(40, 60);
    }

    @Test
    void shortPhaseDoesNotExceedRfcLocalPartCap() {
        // The local part of an email is capped at 64 chars by RFC 5321; SHORT phase
        // must clamp even when minLength would otherwise grow it beyond.
        var schema = StringSchema.builder().format(StringFormat.EMAIL).minLength(70).maxLength(80).build();
        var generator = new EmailGenerator(withSeed(42), schema);

        // when
        var first = generator.generate();

        // then
        assertThat(first).contains("@");
        assertThat(first.indexOf('@')).isLessThanOrEqualTo(64);
    }

    @Test
    void composesWithLengthBounds() {
        var schema = StringSchema.builder().format(StringFormat.EMAIL).minLength(15).maxLength(25).build();
        var generator = new EmailGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThat(s).contains("@");
            assertThat(s.length()).isBetween(15, 25);
        });
    }

}
