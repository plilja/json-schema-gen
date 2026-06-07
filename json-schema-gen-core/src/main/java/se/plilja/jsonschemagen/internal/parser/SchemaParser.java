package se.plilja.jsonschemagen.internal.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.model.SchemaDocument;

public final class SchemaParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static SchemaDocument parse(String jsonSchema) {
        try {
            var rootNode = MAPPER.readTree(jsonSchema);
            var rootSchema = MAPPER.treeToValue(rootNode, Schema.class);
            var refs = new HashMap<String, Schema>();
            // Self-reference always resolves to the same root Schema instance so phase state
            // is shared between the root and any "#" ref.
            refs.put("#", rootSchema);
            collectRefs(rootNode, rootNode, refs);
            return new SchemaDocument(rootSchema, refs);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse JSON Schema", e);
        }
    }

    private static void collectRefs(JsonNode node, JsonNode root, Map<String, Schema> refs)
            throws JsonProcessingException {
        if (node.isObject()) {
            var refNode = node.get("$ref");
            if (refNode != null && refNode.isTextual()) {
                var ref = refNode.asText();
                if (!refs.containsKey(ref)) {
                    refs.put(ref, resolveRef(ref, root));
                }
            }
            for (var property : node.properties()) {
                collectRefs(property.getValue(), root, refs);
            }
        } else if (node.isArray()) {
            for (var item : node) {
                collectRefs(item, root, refs);
            }
        }
    }

    private static Schema resolveRef(String ref, JsonNode root) throws JsonProcessingException {
        if (!ref.startsWith("#")) {
            throw new IllegalArgumentException(
                    "Only internal $ref pointers are supported, got: " + ref);
        }
        var pointer = ref.substring(1);
        var target = root.at(pointer);
        if (target.isMissingNode()) {
            throw new IllegalArgumentException("Unresolved $ref: " + ref);
        }
        return MAPPER.treeToValue(target, Schema.class);
    }
}
