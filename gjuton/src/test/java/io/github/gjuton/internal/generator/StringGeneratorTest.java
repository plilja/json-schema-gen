package io.github.gjuton.internal.generator;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

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
        var schema = StringSchema.builder().minLength(5).build();
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
        var schema = StringSchema.builder().maxLength(8).build();
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
        var schema = StringSchema.builder().minLength(3).build();
        var generator = new StringGenerator(withSeed(42),schema);

        // when
        String first = generator.generate();

        // then
        assertThat(first).hasSize(3);
    }

    @Test
    void emitsBoundaryLengthMaxLength() {
        var schema = StringSchema.builder().maxLength(10).build();
        var generator = new StringGenerator(withSeed(42),schema);

        // when
        String first = generator.generate();

        // then
        assertThat(first).hasSize(10);
    }

    @Test
    void emptyStringSkippedWhenMinLengthPositive() {
        var schema = StringSchema.builder().minLength(2).build();
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
        var schema = StringSchema.builder().pattern("^[A-Z]{3}-\\d{4}$").build();
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
        var schema = StringSchema.builder().minLength(3).maxLength(7).build();
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
        var schema = StringSchema.builder().minLength(5).maxLength(10).pattern("^[a-z]+$").build();
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
        var schema = StringSchema.builder().minLength(5).maxLength(10).pattern("^[a-z]+$").build();
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
    void unknownFormatIsNoOp() {
        var schema = StringSchema.builder().format(StringFormat.UNKNOWN).maxLength(5).build();
        var generator = new StringGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThat(s).matches("[a-z]*");
            assertThat(s).hasSizeLessThanOrEqualTo(5);
        });
    }

    @Test
    void unboundedQuantifierPatternStaysWithinMaxLength() {
        var schema = StringSchema.builder().minLength(3).maxLength(12).pattern("^[a-z]+$").build();
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
