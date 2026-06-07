package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static se.plilja.jsonschemagen.internal.generator.TestContexts.withSeed;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.internal.model.StringSchema;

class StringGeneratorTest {


    @Test
    void firstCallProducesEmptyString() {
        var generator = new StringGenerator(withSeed(42),new StringSchema());

        // when
        String result = generator.generate();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void subsequentCallsProduceNonEmptyStrings() {
        var generator = new StringGenerator(withSeed(42),new StringSchema());
        generator.generate();

        // when
        String second = generator.generate();
        String third = generator.generate();

        // then
        assertThat(second).isNotEmpty();
        assertThat(third).isNotEmpty();
    }

    @Test
    void minLengthRespected() {
        var schema = StringSchema.of(5, null, null);
        var generator = new StringGenerator(withSeed(42),schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s).hasSizeGreaterThanOrEqualTo(5));
    }

    @Test
    void maxLengthRespected() {
        var schema = StringSchema.of(null, 8, null);
        var generator = new StringGenerator(withSeed(42),schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s).hasSizeLessThanOrEqualTo(8));
    }

    @Test
    void emitsBoundaryLengthMinLength() {
        var schema = StringSchema.of(3, null, null);
        var generator = new StringGenerator(withSeed(42),schema);

        // when
        String first = generator.generate();

        // then
        assertThat(first).hasSize(3);
    }

    @Test
    void emitsBoundaryLengthMaxLength() {
        var schema = StringSchema.of(null, 10, null);
        var generator = new StringGenerator(withSeed(42),schema);

        // when
        String first = generator.generate();

        // then
        assertThat(first).hasSize(10);
    }

    @Test
    void emptyStringSkippedWhenMinLengthPositive() {
        var schema = StringSchema.of(2, null, null);
        var generator = new StringGenerator(withSeed(42),schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).noneMatch(String::isEmpty);
    }

    @Test
    void patternConstraintProducesMatchingStrings() {
        var schema = StringSchema.of(null, null, "^[A-Z]{3}-\\d{4}$");
        var generator = new StringGenerator(withSeed(42),schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s).matches("^[A-Z]{3}-\\d{4}$"));
    }

    @Test
    void bothMinAndMaxLengthRespected() {
        var schema = StringSchema.of(3, 7, null);
        var generator = new StringGenerator(withSeed(42),schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThat(s).hasSizeGreaterThanOrEqualTo(3);
            assertThat(s).hasSizeLessThanOrEqualTo(7);
        });
        assertThat(results).anyMatch(s -> s.length() == 3);
        assertThat(results).anyMatch(s -> s.length() == 7);
    }

    @Test
    void patternWithLengthConstraintsRespectsAll() {
        var schema = StringSchema.of(5, 10, "^[a-z]+$");
        var generator = new StringGenerator(withSeed(42),schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThat(s).matches("^[a-z]+$");
            assertThat(s).hasSizeGreaterThanOrEqualTo(5);
            assertThat(s).hasSizeLessThanOrEqualTo(10);
        });
    }

    @Test
    void patternWithLengthConstraintsEmitsBoundaryLengths() {
        var schema = StringSchema.of(5, 10, "^[a-z]+$");
        var generator = new StringGenerator(withSeed(42),schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).anyMatch(s -> s.length() == 5);
        assertThat(results).anyMatch(s -> s.length() == 10);
    }

    @Test
    void unboundedQuantifierPatternStaysWithinMaxLength() {
        var schema = StringSchema.of(3, 12, "^[a-z]+$");
        var generator = new StringGenerator(withSeed(20260607L),schema);

        // when
        var results = IntStream.range(0, 5000)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThat(s).matches("^[a-z]+$");
            assertThat(s.length()).isBetween(3, 12);
        });
    }
}
