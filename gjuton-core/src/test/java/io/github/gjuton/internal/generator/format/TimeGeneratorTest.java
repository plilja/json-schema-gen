package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import java.time.OffsetTime;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TimeGeneratorTest {

    @Test
    void firstResultIsMidnightUtc() {
        // Start-of-day is a routine output from real producers and a classic
        // boundary for time-window comparisons.
        var schema = StringSchema.builder().format(StringFormat.TIME).build();
        var generator = new TimeGenerator(withSeed(42), schema);

        // when
        var first = generator.generate();

        // then
        assertThat(first).isEqualTo("00:00:00Z");
    }

    @Test
    void randomPhaseProducesParseableOffsetTime() {
        var schema = StringSchema.builder().format(StringFormat.TIME).build();
        var generator = new TimeGenerator(withSeed(42), schema);

        // when
        generator.generate();
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThatNoException().isThrownBy(() -> OffsetTime.parse(s)));
    }

    @Test
    void producesVariedValues() {
        var schema = StringSchema.builder().format(StringFormat.TIME).build();
        var generator = new TimeGenerator(withSeed(42), schema);

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
        // Pattern restricts to UTC offset — filters out random non-Z candidates.
        var schema = StringSchema.builder().format(StringFormat.TIME).pattern("Z$").build();
        var generator = new TimeGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThatNoException().isThrownBy(() -> OffsetTime.parse(s));
            assertThat(s).endsWith("Z");
        });
    }

    @Test
    void unsatisfiableMaxLengthThrowsWithClearMessage() {
        // Minimum RFC 3339 time length is 9 chars ("HH:MM:SSZ"); maxLength=8 cannot be satisfied.
        // The error must surface the length problem directly, not as a retry-exhaustion side-effect.
        var schema = StringSchema.builder().format(StringFormat.TIME).maxLength(8).build();
        var generator = new TimeGenerator(withSeed(42), schema);

        // when / then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(UnsatisfiableSchemaException.class)
                .hasMessageContaining("bounds exclude");
    }

    @Test
    void unsatisfiableMinLengthThrowsWithClearMessage() {
        // Maximum RFC 3339 time length is 14 chars ("HH:MM:SS±HH:MM"); minLength=15 cannot be satisfied.
        var schema = StringSchema.builder().format(StringFormat.TIME).minLength(15).build();
        var generator = new TimeGenerator(withSeed(42), schema);

        // when / then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(UnsatisfiableSchemaException.class)
                .hasMessageContaining("bounds exclude");
    }

    @Test
    void composesWithMaxLength() {
        // maxLength=9 admits only the Z-offset form ("HH:MM:SSZ") — non-Z offsets are 14 chars.
        var schema = StringSchema.builder().format(StringFormat.TIME).maxLength(9).build();
        var generator = new TimeGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThatNoException().isThrownBy(() -> OffsetTime.parse(s));
            assertThat(s).hasSize(9);
        });
    }

    @Test
    void minLengthEqualToMaxBoundClearsPreflight() {
        // minLength=MAX_TIME_LENGTH(=14) sits exactly on the upper edge — pre-flight must accept it.
        // Our UTC-only emission only produces 9-char strings, so retry exhaustion eventually throws,
        // but the message must be the generic one, not the pre-flight "bounds exclude" message.
        var schema = StringSchema.builder().format(StringFormat.TIME).minLength(14).build();
        var generator = new TimeGenerator(withSeed(42), schema);

        // when / then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(UnsatisfiableSchemaException.class)
                .hasMessageNotContaining("bounds exclude");
    }

}
