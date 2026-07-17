package io.github.gjuton.internal.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnumUtilTest {

    enum Color { RED, GREEN, BLUE }

    @Test
    void firstReturnsFirstConstant() {
        // when
        var result = EnumUtil.first(Color.class);

        // then
        assertThat(result).isEqualTo(Color.RED);
    }

    @Test
    void nextReturnsNextConstant() {
        // when
        var result = EnumUtil.next(Color.RED);

        // then
        assertThat(result).isEqualTo(Color.GREEN);
    }

    @Test
    void nextClampsAtLastConstant() {
        // when
        var result = EnumUtil.next(Color.BLUE);

        // then
        assertThat(result).isEqualTo(Color.BLUE);
    }
}
