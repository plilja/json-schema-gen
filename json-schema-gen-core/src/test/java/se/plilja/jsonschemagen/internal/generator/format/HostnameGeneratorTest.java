package se.plilja.jsonschemagen.internal.generator.format;

import static org.assertj.core.api.Assertions.assertThat;
import static se.plilja.jsonschemagen.internal.generator.TestContexts.withSeed;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.internal.model.StringFormat;
import se.plilja.jsonschemagen.internal.model.StringSchema;

class HostnameGeneratorTest {

    @Test
    void respectsLengthBounds() {
        var schema = StringSchema.builder().format(StringFormat.HOSTNAME).minLength(5).maxLength(15).build();
        var generator = new HostnameGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isBetween(5, 15));
    }

    @Test
    void producesExpectedHostnamesForFixedSeed() {
        var schema = StringSchema.builder().format(StringFormat.HOSTNAME).build();
        var generator = new HostnameGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 5)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).containsExactly(
                "a.co",
                "hwmarnqdpaaiguewilzorarzv.info",
                "ymkshhv.lp.info",
                "dpcdvbx.sqco.uk",
                "btj.jy.fr");
    }
}
