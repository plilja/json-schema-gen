package io.github.gjuton.internal.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gjuton.internal.parser.SchemaParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaTest {

    @Test
    void getConditionalsIsEmptyWhenSchemaDeclaresNoConditional() {
        var schema = parse("""
                {"type": "string"}
                """);

        // then
        assertThat(schema.getConditionals()).isEmpty();
    }

    @Test
    void getConditionalsReturnsOwnIfThenElseAsASingleEntry() {
        var schema = parse("""
                {
                    "if": {"properties": {"status": {"const": "ok"}}},
                    "then": {"required": ["data"]},
                    "else": {"required": ["error"]}
                }
                """);

        // then
        assertThat(schema.getConditionals()).containsExactly(
                new Schema.Conditional(schema.getIfSchema(), schema.getThenSchema(), schema.getElseSchema()));
    }

    @Test
    void getConditionalsIncludesAccumulatedConditionalsAlongsideItsOwn() {
        var schema = parse("""
                {
                    "if": {"properties": {"status": {"const": "ok"}}},
                    "then": {"required": ["data"]}
                }
                """);
        var accumulated = new Schema.Conditional(parse("""
                {"properties": {"role": {"const": "admin"}}}
                """), parse("""
                {"required": ["scopes"]}
                """), null);
        var merged = schema.toBuilder().additionalConditionals(List.of(accumulated)).build();

        // when
        var conditionals = merged.getConditionals();

        // then
        assertThat(conditionals).containsExactly(
                new Schema.Conditional(schema.getIfSchema(), schema.getThenSchema(), schema.getElseSchema()),
                accumulated);
    }

    private static Schema parse(String json) {
        return SchemaParser.parse(json).getRoot();
    }
}
