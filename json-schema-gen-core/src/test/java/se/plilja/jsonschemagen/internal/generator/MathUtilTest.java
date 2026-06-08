package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MathUtilTest {

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
}
