package se.plilja.jsonschemagen.internal.generator.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static se.plilja.jsonschemagen.internal.generator.TestContexts.withSeed;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.StringFormat;
import se.plilja.jsonschemagen.internal.model.StringSchema;

class UriGeneratorTest {

    @Test
    void producesExpectedUrisForFixedSeed() {
        var schema = StringSchema.builder().format(StringFormat.URI).build();
        var generator = new UriGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 5)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).containsExactly(
                "http://a.co",
                "http://a.co/ahwmarnqdpaaiguewilzorarzvmgtymkshhvglpkffvdpcdvbxjsqcoqzpxbtjgjygup",
                "https://vnnnh.us/dvoyxebbp?c=79",
                "https://pbpzoqg.com/uvr?po=75",
                "http://n.lwojybwq.vguihf.dev/usyuox?vrx=27");
    }

    @Test
    void respectsTightUpperBound() {
        var schema = StringSchema.builder().format(StringFormat.URI).maxLength(30).build();
        var generator = new UriGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isLessThanOrEqualTo(30));
    }

    @Test
    void respectsTightLowerBound() {
        var schema = StringSchema.builder().format(StringFormat.URI).minLength(20).build();
        var generator = new UriGenerator(withSeed(42), schema);

        // when
        var results = IntStream.range(0, 50)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(s -> assertThat(s.length()).isGreaterThanOrEqualTo(20));
    }

    @Test
    void unsatisfiableMaxLengthThrows() {
        // Shortest URI we produce is "http://a.co" = 11 chars; max 10 cannot fit.
        var schema = StringSchema.builder().format(StringFormat.URI).maxLength(10).build();

        // when / then
        assertThatThrownBy(() -> new UriGenerator(withSeed(42), schema))
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void boundaryMaxLengthDoesNotThrow() {
        // maxLength == minReachable (11) is exactly satisfiable.
        var schema = StringSchema.builder().format(StringFormat.URI).maxLength(11).build();

        // when / then
        assertThatNoException().isThrownBy(() -> new UriGenerator(withSeed(42), schema));
    }

    @Test
    void unsatisfiableMinLengthThrows() {
        // We cap URI length at 4096 chars; anything beyond is unreachable.
        var schema = StringSchema.builder().format(StringFormat.URI).minLength(5000).build();

        // when / then
        assertThatThrownBy(() -> new UriGenerator(withSeed(42), schema))
                .isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void boundaryMinLengthDoesNotThrow() {
        // minLength == maxReachable (4096) is exactly satisfiable.
        var schema = StringSchema.builder().format(StringFormat.URI).minLength(4096).build();

        // when / then
        assertThatNoException().isThrownBy(() -> new UriGenerator(withSeed(42), schema));
    }

    @Test
    void emitsMultipleSchemes() {
        var schema = StringSchema.builder().format(StringFormat.URI).build();
        var generator = new UriGenerator(withSeed(42), schema);

        // when
        var schemes = IntStream.range(0, 200)
                .mapToObj(i -> generator.generate())
                .map(s -> s.substring(0, s.indexOf(':')))
                .distinct()
                .toList();

        // then
        assertThat(schemes).contains("https", "mailto", "telnet");
    }
}
