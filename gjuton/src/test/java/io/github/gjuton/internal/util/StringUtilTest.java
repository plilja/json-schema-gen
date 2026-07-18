package io.github.gjuton.internal.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StringUtilTest {

    @Test
    void shortestReturnsShortest() {
        // when
        var result = StringUtil.shortest(List.of("ab", "a", "abc"));

        // then
        assertThat(result).isEqualTo("a");
    }

    @Test
    void shortestWithSingleElement() {
        // when
        var result = StringUtil.shortest(List.of("only"));

        // then
        assertThat(result).isEqualTo("only");
    }

    @Test
    void shortestWithEqualLengthsReturnsFirst() {
        // when
        var result = StringUtil.shortest(List.of("ab", "cd"));

        // then
        assertThat(result).isEqualTo("ab");
    }

    @Test
    void longestReturnsLongest() {
        // when
        var result = StringUtil.longest(List.of("ab", "a", "abc"));

        // then
        assertThat(result).isEqualTo("abc");
    }

    @Test
    void longestWithSingleElement() {
        // when
        var result = StringUtil.longest(List.of("only"));

        // then
        assertThat(result).isEqualTo("only");
    }

    @Test
    void longestWithEqualLengthsReturnsFirst() {
        // when
        var result = StringUtil.longest(List.of("ab", "cd"));

        // then
        assertThat(result).isEqualTo("ab");
    }
}
