package io.github.gjuton.internal.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Random;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MathUtilTest {

    @Nested
    class Max {

        @ParameterizedTest
        @CsvSource({
                "3, 5, 5",
                "5, 3, 5",
                "5, 5, 5",
                "-3, 5, 5",
        })
        void max(Integer a, Integer b, Integer expected) {
            // when / then
            assertThat(MathUtil.max(a, b)).isEqualTo(expected);
        }
    }

    @Nested
    class Min {

        @ParameterizedTest
        @CsvSource({
                "3, 5, 3",
                "5, 3, 3",
                "5, 5, 5",
                "-3, 5, -3",
        })
        void min(Integer a, Integer b, Integer expected) {
            // when / then
            assertThat(MathUtil.min(a, b)).isEqualTo(expected);
        }
    }

    @Nested
    class MaxNullable {

        @ParameterizedTest
        @CsvSource(nullValues = "null", value = {
                "3, 5, 5",
                "5, 3, 5",
                "null, 5, 5",
                "3, null, 3",
                "null, null, null",
        })
        void maxNullable(Integer a, Integer b, Integer expected) {
            // when / then
            assertThat(MathUtil.maxNullable(a, b)).isEqualTo(expected);
        }
    }

    @Nested
    class MinNullable {

        @ParameterizedTest
        @CsvSource(nullValues = "null", value = {
                "3, 5, 3",
                "5, 3, 3",
                "null, 5, 5",
                "3, null, 3",
                "null, null, null",
        })
        void minNullable(Integer a, Integer b, Integer expected) {
            // when / then
            assertThat(MathUtil.minNullable(a, b)).isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "7, 13, 1",
            "12, 18, 6",
            "0, 9, 9",
            "9, 0, 9",
            "-12, 18, 6",
            "12, -18, 6",
    })
    void gcd(long a, long b, long expected) {
        // when / then
        assertThat(MathUtil.gcd(a, b)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "3, 5, 15",
            "4, 12, 12",
            "0, 7, 0",
            "7, 0, 0",
    })
    void lcm(long a, long b, long expected) {
        // when / then
        assertThat(MathUtil.lcm(a, b)).isEqualTo(expected);
    }

    @Test
    void lcmOverflowThrows() {
        // when / then
        assertThatThrownBy(() -> MathUtil.lcm(Long.MAX_VALUE / 2, 7))
                .isInstanceOf(ArithmeticException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "5, 10, 0, 100, 5, 10",
            "0, 100, 5, 20, 5, 20",
            "5, 10, 0, 100, 5, 10",
            "-5, 50, 0, 100, 0, 50",
            "5, 200, 0, 100, 5, 100",
            "-5, 200, 0, 100, 0, 100",
            "50, 20, 0, 100, 50, 50",
            "10, 10, 0, 100, 10, 10",
    })
    void clampRange(int min, int max, int floor, int ceiling, int expectedMin, int expectedMax) {
        // when
        var range = MathUtil.clampRange(min, max, floor, ceiling);

        // then
        assertThat(range.min()).isEqualTo(expectedMin);
        assertThat(range.max()).isEqualTo(expectedMax);
    }

    @ParameterizedTest
    @CsvSource({
            "-50, -10, 0, 100",
            "200, 300, 0, 100",
    })
    void clampRangeRejectsNonOverlappingInput(int min, int max, int floor, int ceiling) {
        // when / then
        assertThatThrownBy(() -> MathUtil.clampRange(min, max, floor, ceiling))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void intRangeRejectsInvertedBounds() {
        // when / then
        assertThatThrownBy(() -> new MathUtil.IntRange(10, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pickRandomReturnsValueWithinRange() {
        var range = new MathUtil.IntRange(5, 10);
        var random = new Random(42);

        // when
        for (int i = 0; i < 100; i++) {
            int value = range.pickRandom(random);

            // then
            assertThat(value).isBetween(5, 10);
        }
    }

    @Test
    void pickRandomOnSingleValueRangeReturnsThatValue() {
        var range = new MathUtil.IntRange(7, 7);

        // when
        int value = range.pickRandom(new Random(42));

        // then
        assertThat(value).isEqualTo(7);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "3, 5, 15",
            "4, 12, 12",
            "null, 7, 7",
            "7, null, 7",
            "null, null, null",
    })
    void lcmNullable(Long a, Long b, Long expected) {
        // when / then
        assertThat(MathUtil.lcmNullable(a, b)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "3, 5, 15",
            "1.5, 1, 3",
            "0.5, 1, 1",
            "2.5, 1, 5",
            "0.5, 0.3, 1.5",
            "null, 7, 7",
            "7, null, 7",
    })
    void lcmNullableBigDecimal(BigDecimal a, BigDecimal b, BigDecimal expected) {
        // when
        var result = MathUtil.lcmNullable(a, b);

        // then
        assertThat(result).isEqualByComparingTo(expected);
    }

    @Test
    void lcmNullableBigDecimalBothNullReturnsNull() {
        // when / then
        assertThat(MathUtil.lcmNullable(null, (BigDecimal) null)).isNull();
    }
}
