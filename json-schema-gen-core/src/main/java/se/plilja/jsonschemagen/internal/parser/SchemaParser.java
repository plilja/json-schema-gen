package se.plilja.jsonschemagen.internal.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.model.SchemaDocument;

/**
 * Parses a JSON Schema document into the internal model. Supports the
 * most common keywords across drafts rather than strict compliance with
 * any single draft version.
 */
public final class SchemaParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parses a JSON Schema string into a {@link SchemaDocument} containing
     * the root schema and all resolved {@code $ref} targets.
     *
     * @throws IllegalArgumentException if the input is not valid JSON or
     *     contains an unresolvable {@code $ref}
     */
    public static SchemaDocument parse(String jsonSchema) {
        try {
            var rootNode = MAPPER.readTree(jsonSchema);
            rewriteTypeArrays(rootNode);
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

    /**
     * Normalises the Draft 7 {@code "type": ["string", "null"]} shorthand
     * into an explicit {@code oneOf} before deserialisation, because the
     * model layer only supports scalar type dispatch.
     *
     * <p>For example,
     * <pre>{@code
     * {
     *     "type": ["string", "null"],
     *     "minLength": 3
     * }
     * }</pre>
     * becomes
     * <pre>{@code
     * {
     *     "oneOf": [
     *         {"type": "string", "minLength": 3},
     *         {"type": "null", "minLength": 3}
     *     ]
     * }
     * }</pre>
     *
     * <p>All properties from the original node are copied into each branch;
     * constraints irrelevant to a given type are silently ignored during
     * deserialisation.
     */
    private static void rewriteTypeArrays(JsonNode node) {
        if (node.isObject()) {
            var objectNode = (ObjectNode) node;
            var typeNode = objectNode.get("type");
            if (typeNode != null && typeNode.isArray()) {
                var oneOfArray = MAPPER.createArrayNode();
                for (var typeElement : typeNode) {
                    var branch = objectNode.deepCopy();
                    branch.set("type", typeElement);
                    oneOfArray.add(branch);
                }
                objectNode.removeAll();
                objectNode.set("oneOf", oneOfArray);
            }
            for (var entry : objectNode.properties()) {
                rewriteTypeArrays(entry.getValue());
            }
        } else if (node.isArray()) {
            for (var element : node) {
                rewriteTypeArrays(element);
            }
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
