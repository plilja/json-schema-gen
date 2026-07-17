package io.github.gjuton.internal.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
