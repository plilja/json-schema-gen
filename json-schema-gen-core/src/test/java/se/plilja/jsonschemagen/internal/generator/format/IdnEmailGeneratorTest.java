package se.plilja.jsonschemagen.internal.generator.format;

import static org.assertj.core.api.Assertions.assertThat;
import static se.plilja.jsonschemagen.internal.generator.TestContexts.withSeed;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.internal.model.StringFormat;
import se.plilja.jsonschemagen.internal.model.StringSchema;

class IdnEmailGeneratorTest {

    @Test
    void singleDeliberateValueEmittedAfterFirstCall() {
        // when
        var schema = StringSchema.builder().format(StringFormat.IDN_EMAIL).build();
        var generator = new IdnEmailGenerator(withSeed(42), schema);

        // then
        assertThat(generator.totalCount()).isEqualTo(1);
        assertThat(generator.emittedCount()).isEqualTo(0);

        // when
        generator.generate();

        // then
        assertThat(generator.emittedCount()).isEqualTo(1);
    }

    @Test
    void producesValueContainingAt() {
        var schema = StringSchema.builder().format(StringFormat.IDN_EMAIL).build();
        var generator = new IdnEmailGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s).contains("@"));
    }

    @Test
    void producesAtLeastOneEmailWithUnicodeCharacters() {
        var schema = StringSchema.builder().format(StringFormat.IDN_EMAIL).build();
        var generator = new IdnEmailGenerator(withSeed(42), schema);

        // when
        var anyHasNonAscii = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .anyMatch(s -> s.codePoints().anyMatch(cp -> cp > 0x7F));

        // then
        assertThat(anyHasNonAscii).isTrue();
    }

    @Test
    void composesWithLengthBounds() {
        var schema = StringSchema.builder().format(StringFormat.IDN_EMAIL).minLength(15).maxLength(25).build();
        var generator = new IdnEmailGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> {
            assertThat(s).contains("@");
            assertThat(s.length()).isBetween(15, 25);
        });
    }

    @Test
    void shortPhaseHonoursMinLength() {
        var schema = StringSchema.builder().format(StringFormat.IDN_EMAIL).minLength(18).build();
        var generator = new IdnEmailGenerator(withSeed(42), schema);

        // when
        var first = generator.generate();

        // then
        assertThat(first).contains("@");
        assertThat(first).hasSize(18);
    }

    @Test
    void producesExpectedEmailsForFixedSeed() {
        var schema = StringSchema.builder().format(StringFormat.IDN_EMAIL).build();
        var generator = new IdnEmailGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 5)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).containsExactly(
                "用@用.中国",
                "用用用用用用用用用用用用用用用用用用用用用用用用用@用.中国",
                "ಬಬಬಟಮಟ@ಡಡಲಡಲಡಲ.ಭಾರತ",
                "я@ияя.рф",
                "ज@अजअअ.भारत");
    }
}
