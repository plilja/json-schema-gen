package io.github.gjuton.internal.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import java.io.IOException;

/**
 * Handles the JSON Schema convention where a bare {@code true} or
 * {@code false} can stand in for a schema object. Returns
 * {@link UntypedSchema} for {@code true}, {@link UnsatisfiableSchema}
 * for {@code false}, and delegates to the default deserializer otherwise.
 */
class SchemaDeserializer extends JsonDeserializer<Schema> {

    @Override
    public Schema deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_TRUE) {
            return new UntypedSchema();
        }
        if (p.currentToken() == JsonToken.VALUE_FALSE) {
            return new UnsatisfiableSchema();
        }
        return p.getCodec().readValue(p, Schema.class);
    }

    @Override
    public Schema deserializeWithType(JsonParser p, DeserializationContext ctxt,
            TypeDeserializer typeDeserializer) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_TRUE) {
            return new UntypedSchema();
        }
        if (p.currentToken() == JsonToken.VALUE_FALSE) {
            return new UnsatisfiableSchema();
        }
        return (Schema) typeDeserializer.deserializeTypedFromObject(p, ctxt);
    }
}
