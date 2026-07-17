package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.TestContexts.withSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

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
    void boundedRandomHostnameProducesValidHostname() {
        var random = new Random(42);

        // when
        var hostnames = IntStream.range(0, 50)
                .mapToObj(i -> HostnameGenerator.randomHostname(Alphabets.EN, random, 4 + (i % 25)))
                .toList();

        // then
        for (int i = 0; i < hostnames.size(); i++) {
            assertThat(hostnames.get(i))
                    .hasSize(4 + (i % 25))
                    .matches("[a-z]+(\\.[a-z]+)+");
        }
    }

    @Test
    void boundedRandomHostnameRejectsLengthBelowMinReachable() {
        var random = new Random(42);

        // when / then
        assertThatThrownBy(() -> HostnameGenerator.randomHostname(Alphabets.EN, random, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundedRandomHostnameAtMinReachableProducesValidHostname() {
        var random = new Random(42);

        // when
        var hostname = HostnameGenerator.randomHostname(Alphabets.EN, random, 4);

        // then
        assertThat(hostname).hasSize(4).matches("[a-z]+\\.[a-z]+");
    }

    @Test
    void minReachableMatchesShortestTld() {
        // then
        assertThat(HostnameGenerator.minReachable(Alphabets.EN)).isEqualTo(4);
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
                "h.fr",
                "arnqdpaaiguewilzor.rzvmgt.y.co",
                "hhvglp.info",
                "dpcdvbx.sqco.uk",
                "btj.jy.fr");
    }
}
