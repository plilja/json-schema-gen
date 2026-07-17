package io.github.gjuton.internal.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deserializes the Draft 7 {@code dependencies} keyword, whose entries
 * are either an array of property names (keys-form) or a sub-schema
 * (schema-form). Returns a {@code Map<String, Object>} where each value
 * is a {@code List<String>} or a {@link Schema}.
 *
 * <p>Keys-form example: {@code "billing_address": ["street", "city"]}
 * produces a {@code List<String>}.
 *
 * <p>Schema-form example:
 * {@code "billing_address": {"properties": {"street": {"type": "string"}}}}
 * produces a {@link Schema}.
 */
class DependenciesDeserializer extends JsonDeserializer<Map<String, Object>> {

    @Override
    public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        var result = new LinkedHashMap<String, Object>();
        if (p.currentToken() != JsonToken.START_OBJECT) {
            return result;
        }
        while (p.nextToken() != JsonToken.END_OBJECT) {
            var key = p.currentName();
            p.nextToken();
            if (p.currentToken() == JsonToken.START_ARRAY) {
                var list = new ArrayList<String>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    list.add(p.getText());
                }
                result.put(key, List.copyOf(list));
            } else if (p.currentToken() == JsonToken.START_OBJECT) {
                var tree = (ObjectNode) p.readValueAsTree();
                if (!tree.has("type")) {
                    tree.put("type", "object");
                }
                result.put(key, p.getCodec().treeToValue(tree, Schema.class));
            }
        }
        return result;
    }
}
