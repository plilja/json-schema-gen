package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import java.time.LocalDate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DateGeneratorTest {

    @Test
    void firstResultIsLeapDay() {
        // 2024-02-29 is a real-world boundary that exposes classic leap-year
        // bugs in date arithmetic; emitting it first guarantees coverage.
        var schema = StringSchema.builder().format(StringFormat.DATE).build();
        var generator = new DateGenerator(withSeed(42), schema);

        // when
        var first = generator.generate();

        // then
        assertThat(first).isEqualTo("2024-02-29");
    }

    @Test
    void randomPhaseProducesParseableDate() {
        var schema = StringSchema.builder().format(StringFormat.DATE).build();
        var generator = new DateGenerator(withSeed(42), schema);

        // when
        generator.generate();
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThatNoException().isThrownBy(() -> LocalDate.parse(s)));
    }

    @Test
    void producesVariedValues() {
        var schema = StringSchema.builder().format(StringFormat.DATE).build();
        var generator = new DateGenerator(withSeed(42), schema);

        // when
        var distinct = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .distinct()
                .count();

        // then
        assertThat(distinct).isGreaterThan(1);
    }

    @Test
    void unsatisfiableMinLengthThrows() {
        // RFC 3339 dates are fixed at 10 characters; a minLength of 11 cannot be satisfied.
        var schema = StringSchema.builder().format(StringFormat.DATE).minLength(11).build();
        var generator = new DateGenerator(withSeed(42), schema);

        // when / then
        assertThatThrownBy(generator::generate).isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void unsatisfiableMaxLengthThrows() {
        var schema = StringSchema.builder().format(StringFormat.DATE).maxLength(9).build();
        var generator = new DateGenerator(withSeed(42), schema);

        // when / then
        assertThatThrownBy(generator::generate).isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void exactBoundaryLengthIsAccepted() {
        // minLength == maxLength == 10 matches every valid RFC 3339 date.
        var schema = StringSchema.builder().format(StringFormat.DATE).minLength(10).maxLength(10).build();
        var generator = new DateGenerator(withSeed(42), schema);

        // when
        var result = generator.generate();

        // then
        assertThat(result).isEqualTo("2024-02-29");
    }

    @Test
    void composesWithPattern() {
        // Pattern restricts to 20xx — exercises the retry loop because most
        // randomly drawn dates from the [1900, 2099] span fall outside the 21st century.
        var schema = StringSchema.builder().format(StringFormat.DATE).pattern("^20").build();
        var generator = new DateGenerator(withSeed(42), schema);

        // when
        // Skip LEAP_DAY (already in 20xx) to force RANDOM-phase generation through the retry loop.
        generator.generate();
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThatNoException().isThrownBy(() -> LocalDate.parse(s));
            assertThat(s).startsWith("20");
        });
    }
}
