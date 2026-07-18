package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import java.time.OffsetDateTime;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DateTimeGeneratorTest {

    @Test
    void firstResultIsY2038Boundary() {
        // 2038-01-19T03:14:07Z is the last second representable in a signed 32-bit
        // time_t. SUTs that still propagate values through 32-bit time arithmetic
        // overflow one second later; emitting it first guarantees that boundary is hit.
        var schema = StringSchema.builder().format(StringFormat.DATE_TIME).build();
        var generator = new DateTimeGenerator(withSeed(42), schema);

        // when
        var first = generator.generate();

        // then
        assertThat(first).isEqualTo("2038-01-19T03:14:07Z");
    }

    @Test
    void secondResultIsLeapDayNoon() {
        // Noon avoids stacking the leap-day boundary with the midnight boundary.
        var schema = StringSchema.builder().format(StringFormat.DATE_TIME).build();
        var generator = new DateTimeGenerator(withSeed(42), schema);

        // when
        generator.generate();
        var second = generator.generate();

        // then
        assertThat(second).isEqualTo("2024-02-29T12:00:00Z");
    }

    @Test
    void thirdResultIsMidnightStartOfYear() {
        // Start-of-year midnight is a routine real-world emission (date upgraded to date-time,
        // start-of-window comparisons) and exposes both year-rollover and start-of-day bugs.
        var schema = StringSchema.builder().format(StringFormat.DATE_TIME).build();
        var generator = new DateTimeGenerator(withSeed(42), schema);

        // when
        generator.generate();
        generator.generate();
        var third = generator.generate();

        // then
        assertThat(third).isEqualTo("2025-01-01T00:00:00Z");
    }

    @Test
    void randomPhaseProducesParseableOffsetDateTime() {
        var schema = StringSchema.builder().format(StringFormat.DATE_TIME).build();
        var generator = new DateTimeGenerator(withSeed(42), schema);

        // when
        generator.generate();
        generator.generate();
        generator.generate();
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThatNoException().isThrownBy(() -> OffsetDateTime.parse(s)));
    }

    @Test
    void producesVariedValues() {
        var schema = StringSchema.builder().format(StringFormat.DATE_TIME).build();
        var generator = new DateTimeGenerator(withSeed(42), schema);

        // when
        var distinct = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .distinct()
                .count();

        // then
        assertThat(distinct).isGreaterThan(2);
    }

    @Test
    void composesWithPattern() {
        // Pattern restricts to UTC offset — random non-Z candidates must be rejected via retry.
        var schema = StringSchema.builder().format(StringFormat.DATE_TIME).pattern("Z$").build();
        var generator = new DateTimeGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThatNoException().isThrownBy(() -> OffsetDateTime.parse(s));
            assertThat(s).endsWith("Z");
        });
    }

    @Test
    void unsatisfiableMaxLengthThrowsWithClearMessage() {
        // Minimum RFC 3339 date-time length is 20 chars ("YYYY-MM-DDTHH:MM:SSZ"); maxLength=19 cannot be satisfied.
        // The error must surface the length problem directly, not as a retry-exhaustion side-effect.
        var schema = StringSchema.builder().format(StringFormat.DATE_TIME).maxLength(19).build();
        var generator = new DateTimeGenerator(withSeed(42), schema);

        // when / then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(UnsatisfiableSchemaException.class)
                .hasMessageContaining("bounds exclude");
    }

    @Test
    void unsatisfiableMinLengthThrowsWithClearMessage() {
        // Maximum RFC 3339 date-time length is 25 chars ("YYYY-MM-DDTHH:MM:SS±HH:MM"); minLength=26 cannot be satisfied.
        var schema = StringSchema.builder().format(StringFormat.DATE_TIME).minLength(26).build();
        var generator = new DateTimeGenerator(withSeed(42), schema);

        // when / then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(UnsatisfiableSchemaException.class)
                .hasMessageContaining("bounds exclude");
    }

    @Test
    void composesWithMaxLength() {
        // maxLength=20 admits only the Z-offset form ("YYYY-MM-DDTHH:MM:SSZ") — non-Z offsets are 25 chars.
        var schema = StringSchema.builder().format(StringFormat.DATE_TIME).maxLength(20).build();
        var generator = new DateTimeGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThatNoException().isThrownBy(() -> OffsetDateTime.parse(s));
            assertThat(s).hasSize(20);
        });
    }

    @Test
    void minLengthEqualToMaxBoundIsAccepted() {
        // minLength=MAX_DATE_TIME_LENGTH(=25) sits exactly on the upper edge — pre-flight must accept it.
        // The 25-char "YYYY-MM-DDTHH:MM:SS±HH:MM" form is reachable through the RANDOM retry loop.
        var schema = StringSchema.builder().format(StringFormat.DATE_TIME).minLength(25).build();
        var generator = new DateTimeGenerator(withSeed(42), schema);

        // when
        var result = generator.generate();

        // then
        assertThat(result).hasSize(25);
    }

}
