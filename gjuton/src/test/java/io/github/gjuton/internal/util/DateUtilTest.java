package io.github.gjuton.internal.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DateUtilTest {

    @Test
    void rangeContainingAnchorYear() {
        // when
        var result = DateUtil.leapDayInRange(instant(2023, 1, 1), instant(2025, 1, 1));

        // then
        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void rangeAfterAnchor() {
        // when
        var result = DateUtil.leapDayInRange(instant(2027, 1, 1), instant(2029, 1, 1));

        // then
        assertThat(result).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void rangeBeforeAnchor() {
        // when
        var result = DateUtil.leapDayInRange(instant(2019, 1, 1), instant(2021, 1, 1));

        // then
        assertThat(result).isEqualTo(LocalDate.of(2020, 2, 29));
    }

    @Test
    void prefersCloserToAnchor() {
        // when
        var result = DateUtil.leapDayInRange(instant(2019, 1, 1), instant(2029, 1, 1));

        // then
        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void noLeapDayInRange() {
        // when
        var result = DateUtil.leapDayInRange(instant(2025, 1, 1), instant(2027, 12, 31));

        // then
        assertThat(result).isNull();
    }

    @Test
    void exactLeapDay() {
        // when
        var result = DateUtil.leapDayInRange(instant(2024, 2, 29), instant(2024, 2, 29));

        // then
        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void skipsNonLeapCenturyYear() {
        // when
        var result = DateUtil.leapDayInRange(instant(2097, 1, 1), instant(2105, 1, 1));

        // then
        assertThat(result).isEqualTo(LocalDate.of(2104, 2, 29));
    }

    @Test
    void leapDayExcludedByDateBounds() {
        // when
        var result = DateUtil.leapDayInRange(instant(2024, 3, 1), instant(2027, 12, 31));

        // then
        assertThat(result).isNull();
    }

    private static Instant instant(int year, int month, int day) {
        return LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
