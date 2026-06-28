package se.plilja.jsonschemagen.internal.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        return doParse(jsonSchema, null);
    }

    /**
     * Parses a JSON Schema file into a {@link SchemaDocument} containing
     * the root schema and all resolved {@code $ref} targets. External
     * {@code $ref} values are resolved relative to the file's parent directory.
     *
     * @throws IllegalArgumentException if the schema is not valid JSON or
     *     contains an unresolvable {@code $ref}
     * @throws UncheckedIOException if reading the file fails
     */
    public static SchemaDocument parse(Path schemaFile) {
        try {
            var jsonSchema = Files.readString(schemaFile);
            return doParse(jsonSchema, schemaFile.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SchemaDocument doParse(String jsonSchema, Path baseDir) {
        try {
            var rootNode = MAPPER.readTree(jsonSchema);
            rewriteTypeArrays(rootNode);
            var rootSchema = MAPPER.treeToValue(rootNode, Schema.class);
            var refs = new HashMap<String, Schema>();
            // Self-reference always resolves to the same root Schema instance so phase state
            // is shared between the root and any "#" ref.
            refs.put("#", rootSchema);
            collectRefs(rootNode, rootNode, refs, baseDir);
            return new SchemaDocument(rootSchema, refs);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse JSON Schema", e);
        }
    }

    /**
     * Normalises the Draft 7 {@code "type": ["string", "null"]} shorthand
     * into an explicit {@code oneOf} before deserialisation. Jackson uses
     * the scalar {@code type} field for subclass dispatch, so the array
     * form must be rewritten before {@code treeToValue} can succeed.
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

    private static void collectRefs(
            JsonNode node, JsonNode root, Map<String, Schema> refs,
            Path baseDir) throws JsonProcessingException {
        if (node.isObject()) {
            var refNode = node.get("$ref");
            if (refNode != null && refNode.isTextual()) {
                var ref = refNode.asText();
                if (!refs.containsKey(ref)) {
                    var resolved = resolveRef(ref, root, baseDir);
                    refs.put(ref, resolved);
                }
            }
            for (var property : node.properties()) {
                collectRefs(property.getValue(), root, refs, baseDir);
            }
        } else if (node.isArray()) {
            for (var item : node) {
                collectRefs(item, root, refs, baseDir);
            }
        }
    }

    /**
     * Resolves a {@code $ref} string to a {@link Schema}. Internal
     * references are resolved within {@code root}; external references
     * are loaded from file or HTTP and resolved by fragment.
     */
    private static Schema resolveRef(String ref, JsonNode root, Path baseDir) throws JsonProcessingException {
        if (ref.startsWith("#")) {
            return resolveFragment(ref.substring(1), root);
        }
        int fragmentIndex = ref.indexOf('#');
        var baseUri = fragmentIndex >= 0 ? ref.substring(0, fragmentIndex) : ref;
        var fragment = fragmentIndex >= 0 ? ref.substring(fragmentIndex + 1) : "";

        var externalRoot = loadExternalDocument(baseUri, baseDir);
        return resolveFragment(fragment, externalRoot);
    }

    private static Schema resolveFragment(String pointer, JsonNode root) throws JsonProcessingException {
        if (pointer.isEmpty()) {
            return MAPPER.treeToValue(root, Schema.class);
        }
        var target = root.at(pointer);
        if (target.isMissingNode()) {
            throw new IllegalArgumentException("Unresolved $ref fragment: #" + pointer);
        }
        return MAPPER.treeToValue(target, Schema.class);
    }

    /**
     * Loads an external JSON Schema document by URI. Relative URIs resolve
     * against {@code baseDir}; HTTP(S) URIs are fetched over the network.
     */
    private static JsonNode loadExternalDocument(String uri, Path baseDir) {
        try {
            JsonNode node;
            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                node = MAPPER.readTree(SchemaFetcher.fetch(uri));
            } else if (baseDir != null) {
                node = MAPPER.readTree(Files.readString(baseDir.resolve(uri)));
            } else {
                throw new IllegalArgumentException(
                        "Cannot resolve relative $ref '" + uri
                                + "': no base URI. Use SchemaParser.parse(Path) to parse from a file.");
            }
            rewriteTypeArrays(node);
            return node;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
