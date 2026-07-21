package io.github.gjuton.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConstraintsTest {

    @Test
    void ofStartsEmpty() {
        // when
        var constraints = Constraints.of();

        // then
        assertThat(constraints.stringMinLength).isNull();
        assertThat(constraints.numberMax).isNull();
        assertThat(constraints.dateMin).isNull();
        assertThat(constraints.alphabet).isNull();
        assertThat(constraints.arrayMaxLength).isNull();
    }

    @Test
    void settersDoNotMutateTheOriginal() {
        // when
        var base = Constraints.of();
        var derived = base.stringLength(1, 5);

        // then
        assertThat(base.stringMaxLength).isNull();
        assertThat(derived.stringMinLength).isEqualTo(1);
        assertThat(derived.stringMaxLength).isEqualTo(5);
    }

    @Test
    void rejectsOneSidedNumberRange() {
        // then
        assertThatThrownBy(() -> Constraints.of().numberRange(BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Constraints.of().numberRange(null, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvertedStringLength() {
        // then
        assertThatThrownBy(() -> Constraints.of().stringLength(5, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeLength() {
        // then
        assertThatThrownBy(() -> Constraints.of().stringLength(-1, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Constraints.of().arrayLength(0, -2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvertedNumberRange() {
        // then
        assertThatThrownBy(() -> Constraints.of().numberRange(5L, 3L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Constraints.of().numberRange(5.0, 3.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Constraints.of().numberRange(new BigDecimal("5"), new BigDecimal("3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvertedDateRange() {
        // then
        assertThatThrownBy(() -> Constraints.of().dateRange(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2019-01-01T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOneSidedDateRange() {
        // then
        assertThatThrownBy(() -> Constraints.of().dateRange(Instant.parse("2020-01-01T00:00:00Z"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Constraints.of().dateRange(null, Instant.parse("2020-01-01T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyAlphabet() {
        // then
        assertThatThrownBy(() -> Constraints.of().alphabet(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Constraints.of().alphabet(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
