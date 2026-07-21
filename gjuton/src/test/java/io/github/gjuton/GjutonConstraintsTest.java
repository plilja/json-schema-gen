package io.github.gjuton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gjuton.api.Constraints;
import io.github.gjuton.api.GenerationMode;
import io.github.gjuton.api.Gjuton;
import io.github.gjuton.errors.UnsatisfiableSchemaException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GjutonConstraintsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int SAMPLES = 100;

    @Test
    void numberRangeNarrowsUnboundedInteger() {
        // when
        var gen = Gjuton.of("""
                { "type": "integer" }""")
                .withConstraints(Constraints.of().numberRange(0, 10))
                .withSeed(1L);

        // then
        forEachValue(gen, node -> assertThat(node.longValue()).isBetween(0L, 10L));
    }

    @Test
    void numberRangeIntersectsSchemaBounds() {
        // when
        var gen = Gjuton.of("""
                { "type": "integer", "minimum": 5, "maximum": 100 }""")
                .withConstraints(Constraints.of().numberRange(0, 10))
                .withSeed(1L);

        // then: schema floor of 5 still wins, constraint ceiling of 10 applies
        forEachValue(gen, node -> assertThat(node.longValue()).isBetween(5L, 10L));
    }

    @Test
    void numberRangeNarrowsNumber() {
        // when
        var gen = Gjuton.of("""
                { "type": "number" }""")
                .withConstraints(Constraints.of().numberRange(0.0, 1.0))
                .withSeed(1L);

        // then
        forEachValue(gen, node -> assertThat(node.doubleValue()).isBetween(0.0, 1.0));
    }

    @Test
    void emptyNumberIntersectionIsUnsatisfiable() {
        // when
        var gen = Gjuton.of("""
                { "type": "integer", "minimum": 50, "maximum": 100 }""")
                .withConstraints(Constraints.of().numberRange(0, 10));

        // then
        assertThatThrownBy(gen::generate).isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void dateRangeNarrowsDate() {
        // when
        var min = Instant.parse("2000-01-01T00:00:00Z");
        var max = Instant.parse("2000-12-31T23:59:59Z");
        var gen = Gjuton.of("""
                { "type": "string", "format": "date" }""")
                .withConstraints(Constraints.of().dateRange(min, max))
                .withSeed(1L);

        // then
        var minDate = LocalDate.ofInstant(min, ZoneOffset.UTC);
        var maxDate = LocalDate.ofInstant(max, ZoneOffset.UTC);
        forEachValue(gen, node -> {
            var date = LocalDate.parse(node.asText());
            assertThat(date).isBetween(minDate, maxDate);
        });
    }

    @Test
    void dateRangeNarrowsDateTime() {
        // when
        var min = Instant.parse("2000-01-01T00:00:00Z");
        var max = Instant.parse("2000-12-31T23:59:59Z");
        var gen = Gjuton.of("""
                { "type": "string", "format": "date-time" }""")
                .withConstraints(Constraints.of().dateRange(min, max))
                .withSeed(1L);

        // then
        forEachValue(gen, node -> {
            var instant = OffsetDateTime.parse(node.asText()).toInstant();
            assertThat(instant).isBetween(min, max);
        });
    }

    @Test
    void stringLengthNarrowsLength() {
        // when
        var gen = Gjuton.of("""
                { "type": "string" }""")
                .withConstraints(Constraints.of().stringLength(2, 4))
                .withSeed(1L);

        // then
        forEachValue(gen, node -> assertThat(node.asText().length()).isBetween(2, 4));
    }

    @Test
    void emptyStringLengthIntersectionIsUnsatisfiable() {
        // when
        var gen = Gjuton.of("""
                { "type": "string", "minLength": 3 }""")
                .withConstraints(Constraints.of().stringLength(0, 2));

        // then
        assertThatThrownBy(gen::generate).isInstanceOf(UnsatisfiableSchemaException.class);
    }

    @Test
    void alphabetRestrictsCharacters() {
        // when
        var gen = Gjuton.of("""
                { "type": "string" }""")
                .withConstraints(Constraints.of().alphabet("ABC").stringLength(5, 5))
                .withSeed(1L);

        // then
        forEachValue(gen, node -> assertThat(node.asText()).matches("[ABC]{5}"));
    }

    @Test
    void arrayLengthNarrowsLength() {
        // when
        var gen = Gjuton.of("""
                { "type": "array", "items": { "type": "integer" } }""")
                .withConstraints(Constraints.of().arrayLength(2, 3))
                .withSeed(1L);

        // then
        forEachValue(gen, node -> assertThat(node.size()).isBetween(2, 3));
    }

    @Test
    void arrayLengthZeroStillReachesFullCoverage() {
        // when: the constraint forces empty arrays, so the element schema is never placed
        var gen = Gjuton.of("""
                { "type": "array", "items": { "type": "integer" } }""")
                .withConstraints(Constraints.of().arrayLength(0, 0))
                .withGenerationMode(GenerationMode.EXHAUSTIVE)
                .withSeed(1L);

        // then: coverage still climbs to 1.0, so a generate-until-covered loop terminates
        for (int i = 0; i < SAMPLES && gen.valueCoverage() < 1.0; i++) {
            var node = parse(gen.generate());
            assertThat(node).isEmpty();
        }
        assertThat(gen.valueCoverage()).isEqualTo(1.0);
    }

    @Test
    void unsetKindsKeepSchemaBehaviour() {
        // when: only string length is constrained on an object with an unbounded integer
        var gen = Gjuton.of("""
                {
                  "type": "object",
                  "properties": { "s": { "type": "string" }, "n": { "type": "integer" } },
                  "required": ["s", "n"]
                }""")
                .withConstraints(Constraints.of().stringLength(3, 3))
                .withSeed(1L);

        // then: strings honour the length while integers range far past it
        boolean sawWideInteger = false;
        for (int i = 0; i < SAMPLES; i++) {
            var node = parse(gen.generate());
            assertThat(node.get("s").asText()).hasSize(3);
            if (Math.abs(node.get("n").longValue()) > 1000) {
                sawWideInteger = true;
            }
        }
        assertThat(sawWideInteger).isTrue();
    }

    private static void forEachValue(Gjuton gen, java.util.function.Consumer<JsonNode> assertion) {
        IntStream.range(0, SAMPLES).forEach(i -> {
            var json = gen.generate();
            var node = parse(json);
            assertion.accept(node);
        });
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
