package se.plilja.jsonschemagen.internal.generator.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static se.plilja.jsonschemagen.internal.generator.TestContexts.withSeed;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.StringFormat;
import se.plilja.jsonschemagen.internal.model.StringSchema;

class IdnHostnameGeneratorTest {

    @Test
    void singleDeliberateValueEmittedAfterFirstCall() {
        // when
        var schema = StringSchema.builder().format(StringFormat.IDN_HOSTNAME).build();
        var generator = new IdnHostnameGenerator(withSeed(42), schema);

        // then
        assertThat(generator.totalCount()).isEqualTo(1);
        assertThat(generator.emittedCount()).isEqualTo(0);

        // when
        generator.generate();

        // then
        assertThat(generator.emittedCount()).isEqualTo(1);
    }

    @Test
    void producesHostnamesContainingADot() {
        var schema = StringSchema.builder().format(StringFormat.IDN_HOSTNAME).build();
        var generator = new IdnHostnameGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s).contains("."));
    }

    @Test
    void respectsLengthBounds() {
        var schema = StringSchema.builder().format(StringFormat.IDN_HOSTNAME).minLength(5).maxLength(15).build();
        var generator = new IdnHostnameGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isBetween(5, 15));
    }

    @Test
    void unsatisfiableMinLengthThrows() {
        var schema = StringSchema.builder().format(StringFormat.IDN_HOSTNAME).minLength(200).build();

        // when / then
        assertThatThrownBy(() -> new IdnHostnameGenerator(withSeed(42), schema))
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void unsatisfiableMaxLengthThrows() {
        var schema = StringSchema.builder().format(StringFormat.IDN_HOSTNAME).maxLength(2).build();

        // when / then
        assertThatThrownBy(() -> new IdnHostnameGenerator(withSeed(42), schema))
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void producesAtLeastOneHostnameWithUnicodeCharacters() {
        var schema = StringSchema.builder().format(StringFormat.IDN_HOSTNAME).build();
        var generator = new IdnHostnameGenerator(withSeed(42), schema);

        // when
        var anyHasNonAscii = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .anyMatch(s -> s.codePoints().anyMatch(cp -> cp > 0x7F));

        // then
        assertThat(anyHasNonAscii).isTrue();
    }

    @Test
    void producesExpectedHostnamesForFixedSeed() {
        var schema = StringSchema.builder().format(StringFormat.IDN_HOSTNAME).build();
        var generator = new IdnHostnameGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 5)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).containsExactly(
                "用.中国",
                "户子户例例用子户户户户例户子子子子用.户例子户子用.用.中国",
                "рммляя.рф",
                "dpcdvbx.sqco.uk",
                "例户子.用子.中国");
    }
}
