package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static se.plilja.jsonschemagen.internal.generator.TestContexts.withSeed;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.NumericSchema;

class NumericGeneratorTest {

    @Nested
    class IntegerTests {

        @Test
        void unconstrainedFirstCallProducesZero() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").build());

            // when
            var result = generator.generate();

            // then
            assertThat(result).isEqualTo(0L);
        }

        @Test
        void unconstrainedSubsequentCallsProduceVariedValues() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").build());
            generator.generate();

            // when
            var values = LongStream.range(0, 20)
                    .mapToObj(i -> generator.generate())
                    .collect(Collectors.toSet());

            // then
            assertThat(values).hasSizeGreaterThan(1);
        }

        @Test
        void boundedCoversBoundaryValues() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(-10)).maximum(BigDecimal.valueOf(10)).build());

            // when
            List<Number> values = LongStream.range(0, 20)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).contains(-10L, 10L, 0L, -9L, 9L);
        }

        @Test
        void boundedAllValuesWithinRange() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(-10)).maximum(BigDecimal.valueOf(10)).build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.longValue() >= -10 && v.longValue() <= 10);
        }

        @Test
        void minOnlyCoversBoundaryValues() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(-5)).build());

            // when
            List<Number> values = LongStream.range(0, 20)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).contains(-5L, 0L, -4L);
            assertThat(values).allMatch(v -> v.longValue() >= -5);
        }

        @Test
        void exclusiveMinimumExcludesBound() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").maximum(BigDecimal.valueOf(10)).exclusiveMinimum(BigDecimal.valueOf(5)).build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.longValue() > 5 && v.longValue() <= 10);
            assertThat(values).contains(6L, 10L);
        }

        @Test
        void exclusiveMaximumExcludesBound() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(-10)).exclusiveMaximum(BigDecimal.valueOf(5)).build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.longValue() >= -10 && v.longValue() < 5);
            assertThat(values).contains(-10L, 4L);
        }

        @Test
        void exclusiveBoundsOnlyCoversBoundaryValues() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").exclusiveMinimum(BigDecimal.valueOf(-10)).exclusiveMaximum(BigDecimal.valueOf(10)).build());

            // when
            List<Number> values = LongStream.range(0, 20)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.longValue() > -10 && v.longValue() < 10);
            assertThat(values).contains(-9L, 9L, 0L, -8L, 8L);
        }

        @Test
        void multipleOfAllValuesAreMultiples() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").multipleOf(BigDecimal.valueOf(7)).build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.longValue() % 7 == 0);
        }

        @Test
        void unboundedMultipleOfStaysWithinJsonSafeIntegerRange() {
            long safeMax = (1L << 53) - 1;
            var generator = new NumericGenerator(withSeed(20260607L),
                    NumericSchema.builder().type("integer").multipleOf(BigDecimal.valueOf(5)).build());

            // when
            List<Number> values = LongStream.range(0, 1000)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.longValue() >= -safeMax && v.longValue() <= safeMax);
            assertThat(values).allMatch(v -> v.longValue() % 5 == 0);
        }

        @Test
        void multipleOfWithBoundsCoversBoundaryMultiples() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(-20)).maximum(BigDecimal.valueOf(20)).multipleOf(BigDecimal.valueOf(7)).build());

            // when
            List<Number> values = LongStream.range(0, 20)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.longValue() >= -20 && v.longValue() <= 20 && v.longValue() % 7 == 0);
            assertThat(values).contains(-14L, 14L, 0L);
        }

        @Test
        void multipleOfWithBoundsAllValuesValid() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(-20)).maximum(BigDecimal.valueOf(20)).multipleOf(BigDecimal.valueOf(7)).build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.longValue() >= -20 && v.longValue() <= 20 && v.longValue() % 7 == 0);
        }

        @Test
        void multipleOfWithExclusiveBounds() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").exclusiveMinimum(BigDecimal.valueOf(-15)).exclusiveMaximum(BigDecimal.valueOf(15)).multipleOf(BigDecimal.valueOf(7)).build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.longValue() > -15 && v.longValue() < 15 && v.longValue() % 7 == 0);
            assertThat(values).contains(-14L, 14L, 0L);
        }

        @Test
        void maxOnlyCoversBoundaryValues() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").maximum(BigDecimal.valueOf(5)).build());

            // when
            List<Number> values = LongStream.range(0, 20)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).contains(5L, 0L, 4L);
            assertThat(values).allMatch(v -> v.longValue() <= 5);
        }

        @Test
        void minimumAndExclusiveMinimumTakeTighterBound() {
            // Both inclusive and exclusive lower bounds set — effective floor is max(0, 10+1) = 11.
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(0)).exclusiveMinimum(BigDecimal.valueOf(10)).maximum(BigDecimal.valueOf(20)).build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.longValue() > 10 && v.longValue() <= 20);
            assertThat(values).contains(11L, 20L);
        }

        @Test
        void maximumAndExclusiveMaximumTakeTighterBound() {
            // Both inclusive and exclusive upper bounds set — effective ceiling is min(20, 10-1) = 9.
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(0)).maximum(BigDecimal.valueOf(20)).exclusiveMaximum(BigDecimal.valueOf(10)).build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.longValue() >= 0 && v.longValue() < 10);
            assertThat(values).contains(0L, 9L);
        }

        @Test
        void randomPhaseCoversEntireBoundedRange() {
            // The deterministic phases (MIN, MAX, NEAR_*) emit each boundary exactly once.
            // High counts (>50 in 1000 iterations) for both 0 and 3 prove the RANDOM phase
            // itself reaches the upper bound, not just the MAX phase.
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(0)).maximum(BigDecimal.valueOf(3)).build());

            // when
            var counts = LongStream.range(0, 1000)
                    .mapToObj(i -> generator.generate().longValue())
                    .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

            // then
            assertThat(counts.get(0L)).isGreaterThan(50L);
            assertThat(counts.get(3L)).isGreaterThan(50L);
        }

        @Test
        void fractionalExclusiveMinimumProducesValidValues() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer")
                            .exclusiveMinimum(new BigDecimal("0.5"))
                            .maximum(BigDecimal.valueOf(1))
                            .build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then — only valid integer is 1 (1 > 0.5 and 1 ≤ 1)
            assertThat(values).allMatch(v -> v.longValue() == 1L);
        }

        @Test
        void fractionalExclusiveMaximumProducesValidValues() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer")
                            .minimum(BigDecimal.valueOf(1))
                            .exclusiveMaximum(new BigDecimal("1.5"))
                            .build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then — only valid integer is 1 (1 ≥ 1 and 1 < 1.5)
            assertThat(values).allMatch(v -> v.longValue() == 1L);
        }

        @Test
        void fractionalExclusiveBothBoundsProducesValidValues() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer")
                            .exclusiveMinimum(new BigDecimal("-0.5"))
                            .exclusiveMaximum(new BigDecimal("1.5"))
                            .build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then — valid integers are 0 and 1 (0 > -0.5, 1 < 1.5)
            assertThat(values).allMatch(v -> v.longValue() >= 0L && v.longValue() <= 1L);
            assertThat(values).contains(0L, 1L);
        }

        @Test
        void fractionalMultipleOfProducesIntegerMultiples() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer")
                            .minimum(new BigDecimal("4.5"))
                            .multipleOf(new BigDecimal("1.5"))
                            .build());

            // when
            var values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then — lcm(1.5, 1) = 3, so valid values ≥ 4.5 are 6, 9, 12, …
            assertThat(values).allMatch(v -> v instanceof Long);
            assertThat(values).allMatch(v -> v.longValue() >= 6 && v.longValue() % 3 == 0);
            assertThat(values).contains(6L, 9L);
        }

        @Test
        void fractionalMultipleOfHalfProducesEveryInteger() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer")
                            .minimum(BigDecimal.valueOf(0))
                            .maximum(BigDecimal.valueOf(5))
                            .multipleOf(new BigDecimal("0.5"))
                            .build());

            // when
            var values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then — lcm(0.5, 1) = 1, so every integer in [0, 5] is valid
            assertThat(values).allMatch(v -> v instanceof Long);
            assertThat(values).allMatch(v -> v.longValue() >= 0 && v.longValue() <= 5);
            assertThat(values).contains(0L, 1L, 2L, 3L, 4L, 5L);
        }

        @Test
        void fractionalMultipleOfTwoPointFiveProducesMultiplesOfFive() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer")
                            .minimum(BigDecimal.valueOf(0))
                            .maximum(BigDecimal.valueOf(20))
                            .multipleOf(new BigDecimal("2.5"))
                            .build());

            // when
            var values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then — lcm(2.5, 1) = 5, so valid values are 0, 5, 10, 15, 20
            assertThat(values).allMatch(v -> v instanceof Long);
            assertThat(values).allMatch(v -> v.longValue() >= 0 && v.longValue() <= 20 && v.longValue() % 5 == 0);
            assertThat(values).contains(0L, 5L, 10L, 15L, 20L);
        }

        @Test
        void randomPhaseCoversEntireMultipleOfRange() {
            // The deterministic phases (MIN, MAX, NEAR_*) emit each boundary multiple exactly
            // once. High counts (>50 in 1000 iterations) for both 0 and 20 prove the RANDOM
            // phase itself reaches the highest valid multiple, not just the MAX phase.
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(0)).maximum(BigDecimal.valueOf(20)).multipleOf(BigDecimal.valueOf(5)).build());

            // when
            var counts = LongStream.range(0, 1000)
                    .mapToObj(i -> generator.generate().longValue())
                    .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

            // then
            assertThat(counts.get(0L)).isGreaterThan(50L);
            assertThat(counts.get(20L)).isGreaterThan(50L);
        }

        @Test
        void contradictoryBoundsThrowsUnsatisfiableSchemaException() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(600)).maximum(BigDecimal.valueOf(500)).build());

            // when / then
            assertThatThrownBy(generator::generate).isInstanceOf(UnsatisfiableSchemaException.class);
        }
    }

    @Nested
    class NumberTests {

        @Test
        void numberTypeProducesDoubles() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("number").minimum(BigDecimal.valueOf(0)).maximum(BigDecimal.valueOf(10)).build());

            // when
            var result = generator.generate();

            // then
            assertThat(result).isInstanceOf(Double.class);
        }

        @Test
        void numberTypeBoundedAllValuesWithinRange() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("number").minimum(BigDecimal.valueOf(0)).maximum(BigDecimal.valueOf(10)).build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.doubleValue() >= 0.0 && v.doubleValue() <= 10.0);
            assertThat(values).allMatch(v -> v instanceof Double);
        }

        @Test
        void numberTypeWithFractionalBoundsStaysInRange() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("number")
                            .minimum(new BigDecimal("1.5"))
                            .maximum(new BigDecimal("4.5"))
                            .build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.doubleValue() >= 1.5 && v.doubleValue() <= 4.5);
        }

        @Test
        void numberTypeWithMultipleOfGeneratesValidMultiples() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("number")
                            .minimum(new BigDecimal("0"))
                            .maximum(new BigDecimal("10"))
                            .multipleOf(new BigDecimal("0.5"))
                            .build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> {
                double d = v.doubleValue();
                return d >= 0.0 && d <= 10.0 && Math.abs(d % 0.5) < 1e-9;
            });
        }

        @Test
        void numberTypeExclusiveBoundsExcludeEndpoints() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("number")
                            .exclusiveMinimum(new BigDecimal("0"))
                            .exclusiveMaximum(new BigDecimal("1"))
                            .build());

            // when
            List<Number> values = LongStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .toList();

            // then
            assertThat(values).allMatch(v -> v.doubleValue() > 0.0 && v.doubleValue() < 1.0);
        }

        @Test
        void numberTypeUnconstrainedProducesVariedDoubles() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("number").build());
            generator.generate();

            // when
            var values = LongStream.range(0, 20)
                    .mapToObj(i -> generator.generate())
                    .collect(Collectors.toSet());

            // then
            assertThat(values).hasSizeGreaterThan(1);
            assertThat(values).allMatch(v -> v instanceof Double);
        }

        @Test
        void minimumEqualsMaximumProducesThatValue() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("number").minimum(BigDecimal.valueOf(1)).maximum(BigDecimal.valueOf(1)).build());

            // when
            var result = generator.generate();

            // then
            assertThat(result).isEqualTo(1.0);
        }

        @Test
        void contradictoryBoundsThrowsUnsatisfiableSchemaException() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("number").minimum(BigDecimal.valueOf(5)).maximum(BigDecimal.valueOf(1)).build());

            // when / then
            assertThatThrownBy(generator::generate).isInstanceOf(UnsatisfiableSchemaException.class);
        }
    }

    @Nested
    class CoverageCounts {

        @Test
        void totalCountIsPhaseCountIncludingRandomSlot() {
            // when
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(-10)).maximum(BigDecimal.valueOf(10)).build());

            // then
            assertThat(generator.totalCount()).isEqualTo(6);
        }

        @Test
        void emittedCountStartsAtZero() {
            // when
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(-10)).maximum(BigDecimal.valueOf(10)).build());

            // then
            assertThat(generator.emittedCount()).isEqualTo(0);
        }

        @Test
        void emittedCountReachesTotalOnlyAfterRandomEmitted() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").minimum(BigDecimal.valueOf(-10)).maximum(BigDecimal.valueOf(10)).build());

            // when: five boundary phases (MIN,MAX,ZERO,NEAR_MIN,NEAR_MAX)
            for (int i = 0; i < 5; i++) {
                generator.generate();
            }

            // then: boundaries emitted, random slot still open
            assertThat(generator.emittedCount()).isEqualTo(5);
            assertThat(generator.totalCount()).isEqualTo(6);

            // when: one more call emits the random slot
            generator.generate();

            // then
            assertThat(generator.emittedCount()).isEqualTo(6);
        }

        @Test
        void skippedBoundaryPhasesStillAdvanceCoverage() {
            var generator = new NumericGenerator(withSeed(42),
                    NumericSchema.builder().type("integer").build());

            // when: enough calls to walk past all skipped boundaries into random
            for (int i = 0; i < 10; i++) {
                generator.generate();
            }

            // then: unconstrained reaches full coverage despite skipped MIN/MAX/NEAR
            assertThat(generator.emittedCount()).isEqualTo(generator.totalCount());
        }
    }

}
