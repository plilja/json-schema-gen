package io.github.gjuton.internal.parser;

import static java.util.Map.entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.gjuton.internal.model.UntypedSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fills in the {@code type} keyword for schemas that omit it but whose
 * present keywords imply exactly one type, so those keywords survive
 * deserialisation instead of being silently dropped by
 * {@link UntypedSchema}.
 *
 * <p>Only recurses into positions that hold sub-schemas ({@code properties}
 * values, {@code items}, {@code oneOf}/{@code anyOf}/{@code allOf}
 * elements, etc.) — never into {@code const}/{@code enum} payloads or
 * object keys, which may coincidentally share a name with a schema
 * keyword (e.g. a property named {@code "pattern"}).
 */
final class TypeInferrer {

    /**
     * Describes how a JSON Schema keyword's value relates to sub-schemas,
     * so the walker knows how to recurse into it.
     */
    private enum SchemaShape {
        /** The value is a single sub-schema (e.g. {@code if}, {@code not}). */
        SCHEMA,
        /** The value is an array of sub-schemas (e.g. {@code oneOf}). */
        SCHEMA_ARRAY,
        /** The value is an object whose values are sub-schemas (e.g. {@code properties}). */
        SCHEMA_MAP,
        /** The value is either a single sub-schema or an array of sub-schemas (e.g. {@code items}). */
        SCHEMA_OR_SCHEMA_ARRAY
    }

    private static final List<String> OBJECT_KEYWORDS = List.of(
            "properties", "required", "additionalProperties",
            "minProperties", "maxProperties", "dependencies",
            "dependentRequired", "dependentSchemas",
            "patternProperties", "propertyNames");

    private static final List<String> STRING_KEYWORDS = List.of(
            "pattern", "minLength", "maxLength", "format");

    private static final List<String> NUMBER_KEYWORDS = List.of(
            "minimum", "maximum", "exclusiveMinimum",
            "exclusiveMaximum", "multipleOf");

    private static final List<String> ARRAY_KEYWORDS = List.of(
            "items", "prefixItems", "additionalItems",
            "contains", "minItems", "maxItems", "uniqueItems");

    private static final Map<String, SchemaShape> SCHEMA_FIELDS = Map.ofEntries(
            entry("properties", SchemaShape.SCHEMA_MAP),
            entry("definitions", SchemaShape.SCHEMA_MAP),
            entry("$defs", SchemaShape.SCHEMA_MAP),
            entry("dependentSchemas", SchemaShape.SCHEMA_MAP),
            entry("patternProperties", SchemaShape.SCHEMA_MAP),
            entry("oneOf", SchemaShape.SCHEMA_ARRAY),
            entry("anyOf", SchemaShape.SCHEMA_ARRAY),
            entry("allOf", SchemaShape.SCHEMA_ARRAY),
            entry("prefixItems", SchemaShape.SCHEMA_ARRAY),
            entry("items", SchemaShape.SCHEMA_OR_SCHEMA_ARRAY),
            entry("additionalItems", SchemaShape.SCHEMA),
            entry("contains", SchemaShape.SCHEMA),
            entry("additionalProperties", SchemaShape.SCHEMA),
            entry("if", SchemaShape.SCHEMA),
            entry("then", SchemaShape.SCHEMA),
            entry("else", SchemaShape.SCHEMA),
            entry("not", SchemaShape.SCHEMA),
            entry("propertyNames", SchemaShape.SCHEMA)
    );

    private TypeInferrer() {
    }

    /**
     * Walks the given JSON tree and adds a {@code "type"} field to every
     * object node that (a) lacks one and (b) contains keywords implying
     * exactly one JSON Schema type.
     */
    static void inferMissingTypes(JsonNode node) {
        if (!node.isObject()) {
            return;
        }
        var objectNode = (ObjectNode) node;
        if (!objectNode.has("type")) {
            var inferred = inferType(objectNode);
            if (inferred != null) {
                objectNode.put("type", inferred);
            }
        }
        for (var field : SCHEMA_FIELDS.entrySet()) {
            var value = objectNode.get(field.getKey());
            if (value == null) {
                continue;
            }
            switch (field.getValue()) {
                case SCHEMA -> inferMissingTypes(value);
                case SCHEMA_ARRAY -> {
                    if (value.isArray()) {
                        for (var element : value) {
                            inferMissingTypes(element);
                        }
                    }
                }
                case SCHEMA_MAP -> {
                    if (value.isObject()) {
                        for (var entry : value.properties()) {
                            inferMissingTypes(entry.getValue());
                        }
                    }
                }
                case SCHEMA_OR_SCHEMA_ARRAY -> {
                    if (value.isArray()) {
                        for (var element : value) {
                            inferMissingTypes(element);
                        }
                    } else {
                        inferMissingTypes(value);
                    }
                }
                default -> throw new IllegalStateException("Unhandled field type: " + field.getValue());
            }
        }
        inferMissingTypesInDependencies(objectNode);
    }

    /**
     * In Draft 7 {@code dependencies}, each entry is either a sub-schema
     * (object) or a property-name list (array). Only the sub-schema form
     * is walked.
     */
    private static void inferMissingTypesInDependencies(ObjectNode node) {
        var value = node.get("dependencies");
        if (value != null && value.isObject()) {
            for (var entry : value.properties()) {
                if (entry.getValue().isObject()) {
                    inferMissingTypes(entry.getValue());
                }
            }
        }
    }

    /**
     * Returns the single type implied by {@code node}'s present keywords,
     * or {@code null} if no type-specific keyword is present or keywords
     * from more than one type are mixed.
     */
    private static String inferType(ObjectNode node) {
        var candidates = new ArrayList<String>(4);
        if (hasAny(node, OBJECT_KEYWORDS)) {
            candidates.add("object");
        }
        if (hasAny(node, STRING_KEYWORDS)) {
            candidates.add("string");
        }
        if (hasAny(node, NUMBER_KEYWORDS)) {
            candidates.add("number");
        }
        if (hasAny(node, ARRAY_KEYWORDS)) {
            candidates.add("array");
        }
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    private static boolean hasAny(ObjectNode node, List<String> keywords) {
        return keywords.stream().anyMatch(node::has);
    }
}
