package io.github.gjuton.internal.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RandomUtilTest {

    @Nested
    class RandomOne {

        @Test
        void returnsAnElementFromTheList() {
            var items = List.of("a", "b", "c", "d", "e");

            // when
            var pick = RandomUtil.randomOne(items, new Random(42));

            // then
            assertThat(items).contains(pick);
        }

        @Test
        void usesProvidedRandom() {
            // Two seeds should produce different picks for a sufficiently large list.
            var items = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");

            // when
            var first = RandomUtil.randomOne(items, new Random(1));
            var second = RandomUtil.randomOne(items, new Random(2));

            // then
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        void coversAllElementsAcrossManyDraws() {
            var items = List.of("a", "b", "c");
            var random = new Random(42);

            // when
            var picks = IntStream.range(0, 100)
                    .mapToObj(i -> RandomUtil.randomOne(items, random))
                    .distinct()
                    .toList();

            // then
            assertThat(picks).containsExactlyInAnyOrderElementsOf(items);
        }
    }

    @Nested
    class RandomStringOfLength {

        @Test
        void producesRequestedLength() {
            // when
            var result = RandomUtil.randomStringOfLength(15, new Random(42));

            // then
            assertThat(result).hasSize(15);
        }

        @Test
        void usesOnlyDefaultAlphabetCharacters() {
            // when
            var result = RandomUtil.randomStringOfLength(50, new Random(42));

            // then
            assertThat(result).matches("[a-z]+");
        }

        @Test
        void usesOnlyCharactersFromProvidedAlphabet() {
            // when
            var result = RandomUtil.randomStringOfLength("xy", 30, new Random(42));

            // then
            assertThat(result).matches("[xy]+");
        }

        @Test
        void drawsWholeCodePointsFromSupplementaryPlaneAlphabet() {
            // when
            var result = RandomUtil.randomStringOfLength("😀🎉", 10, new Random(42));

            // then: whole emoji only — never a lone surrogate — and length counts code points
            var codePoints = result.codePoints().toArray();
            int grinning = "😀".codePointAt(0);
            int party = "🎉".codePointAt(0);
            assertThat(codePoints).hasSize(10);
            assertThat(codePoints).containsOnly(grinning, party);
        }

        @Test
        void usesProvidedRandom() {
            // when
            var first = RandomUtil.randomStringOfLength(20, new Random(1));
            var second = RandomUtil.randomStringOfLength(20, new Random(2));

            // then
            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    class RandomSubset {

        @Test
        void returnsRequestedSize() {
            var items = List.of("a", "b", "c", "d", "e");

            // when
            var subset = RandomUtil.randomSubset(items, 3, new Random(42));

            // then
            assertThat(subset).hasSize(3);
        }

        @Test
        void containsOnlyOriginalElements() {
            var items = List.of("a", "b", "c", "d", "e");

            // when
            var subset = RandomUtil.randomSubset(items, 3, new Random(42));

            // then
            assertThat(items).containsAll(subset);
        }

        @Test
        void containsNoDuplicates() {
            var items = List.of("a", "b", "c", "d", "e");

            // when
            var subset = RandomUtil.randomSubset(items, 4, new Random(42));

            // then
            assertThat(subset).doesNotHaveDuplicates();
        }

        @Test
        void ofFullSizeReturnsAllElements() {
            var items = List.of("a", "b", "c");

            // when
            var subset = RandomUtil.randomSubset(items, 3, new Random(42));

            // then
            assertThat(subset).containsExactlyInAnyOrderElementsOf(items);
        }

        @Test
        void ofSizeZeroIsEmpty() {
            var items = List.of("a", "b", "c");

            // when
            var subset = RandomUtil.randomSubset(items, 0, new Random(42));

            // then
            assertThat(subset).isEmpty();
        }

        @Test
        void usesProvidedRandom() {
            // Two different seeds should (with high probability for a 5-element list)
            // produce different orderings or different element selections.
            var items = List.of("a", "b", "c", "d", "e");

            // when
            var first = RandomUtil.randomSubset(items, 3, new Random(1));
            var second = RandomUtil.randomSubset(items, 3, new Random(2));

            // then
            assertThat(first).isNotEqualTo(second);
        }
    }
}
