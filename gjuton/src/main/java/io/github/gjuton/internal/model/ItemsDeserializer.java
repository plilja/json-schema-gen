package io.github.gjuton.internal.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deserializes the JSON Schema {@code items} keyword.
 *
 * <p>The keyword accepts several shapes:
 * <ul>
 *     <li>A schema object — returns a {@link Schema} (uniform items)</li>
 *     <li>An array of schemas — returns a {@code List<Schema>} (Draft 7 tuple)</li>
 *     <li>{@code true} — returns {@link UntypedSchema} (any element allowed)</li>
 *     <li>{@code false} — returns {@link UnsatisfiableSchema} (no elements allowed)</li>
 * </ul>
 */
class ItemsDeserializer extends JsonDeserializer<Object> {

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_TRUE) {
            return new UntypedSchema();
        }
        if (p.currentToken() == JsonToken.VALUE_FALSE) {
            return new UnsatisfiableSchema();
        }
        if (p.currentToken() == JsonToken.START_ARRAY) {
            var schemas = new ArrayList<Schema>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                schemas.add(p.getCodec().readValue(p, Schema.class));
            }
            return List.copyOf(schemas);
        }
        return p.getCodec().readValue(p, Schema.class);
    }
}
