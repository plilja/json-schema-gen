package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FunctionalUtilTest {

    @Nested
    class Coalesce {

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
            assertThat(FunctionalUtil.max(a, b)).isEqualTo(expected);
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
            assertThat(FunctionalUtil.min(a, b)).isEqualTo(expected);
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
            assertThat(FunctionalUtil.maxNullable(a, b)).isEqualTo(expected);
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
            assertThat(FunctionalUtil.minNullable(a, b)).isEqualTo(expected);
        }
    }

    @Nested
    class RandomSubset {

        @Test
        void returnsRequestedSize() {
            var items = List.of("a", "b", "c", "d", "e");

            // when
            var subset = FunctionalUtil.randomSubset(items, 3, new Random(42));

            // then
            assertThat(subset).hasSize(3);
        }

        @Test
        void containsOnlyOriginalElements() {
            var items = List.of("a", "b", "c", "d", "e");

            // when
            var subset = FunctionalUtil.randomSubset(items, 3, new Random(42));

            // then
            assertThat(items).containsAll(subset);
        }

        @Test
        void containsNoDuplicates() {
            var items = List.of("a", "b", "c", "d", "e");

            // when
            var subset = FunctionalUtil.randomSubset(items, 4, new Random(42));

            // then
            assertThat(subset).doesNotHaveDuplicates();
        }

        @Test
        void ofFullSizeReturnsAllElements() {
            var items = List.of("a", "b", "c");

            // when
            var subset = FunctionalUtil.randomSubset(items, 3, new Random(42));

            // then
            assertThat(subset).containsExactlyInAnyOrderElementsOf(items);
        }

        @Test
        void ofSizeZeroIsEmpty() {
            var items = List.of("a", "b", "c");

            // when
            var subset = FunctionalUtil.randomSubset(items, 0, new Random(42));

            // then
            assertThat(subset).isEmpty();
        }

        @Test
        void usesProvidedRandom() {
            // Two different seeds should (with high probability for a 5-element list)
            // produce different orderings or different element selections.
            var items = List.of("a", "b", "c", "d", "e");

            // when
            var first = FunctionalUtil.randomSubset(items, 3, new Random(1));
            var second = FunctionalUtil.randomSubset(items, 3, new Random(2));

            // then
            assertThat(first).isNotEqualTo(second);
        }
    }
}
