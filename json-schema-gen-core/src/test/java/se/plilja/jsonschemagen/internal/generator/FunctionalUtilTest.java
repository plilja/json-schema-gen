package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FunctionalUtilTest {

    @Test
    void returnsFirstNonNullValue() {
        // when
        String result = FunctionalUtil.coalesce(null, "a", "b");

        // then
        assertThat(result).isEqualTo("a");
    }

    @Test
    void returnsFirstValueWhenNotNull() {
        // when
        String result = FunctionalUtil.coalesce("a", "b");

        // then
        assertThat(result).isEqualTo("a");
    }

    @Test
    void returnsNullWhenAllNull() {
        // when
        String result = FunctionalUtil.coalesce(null, null);

        // then
        assertThat(result).isNull();
    }

    @Test
    void skipsMultipleLeadingNulls() {
        // when
        Integer result = FunctionalUtil.coalesce(null, null, null, 42);

        // then
        assertThat(result).isEqualTo(42);
    }

    @ParameterizedTest
    @CsvSource({
            "3, 5, 5",
            "5, 3, 5",
            "5, 5, 5",
            "-3, 5, 5",
    })
    void max(Integer a, Integer b, Integer expected) {
        // when / then
        assertThat(FunctionalUtil.max(a, b)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "3, 5, 3",
            "5, 3, 3",
            "5, 5, 5",
            "-3, 5, -3",
    })
    void min(Integer a, Integer b, Integer expected) {
        // when / then
        assertThat(FunctionalUtil.min(a, b)).isEqualTo(expected);
    }

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
        assertThat(FunctionalUtil.maxNullable(a, b)).isEqualTo(expected);
    }

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
        assertThat(FunctionalUtil.minNullable(a, b)).isEqualTo(expected);
    }
}
