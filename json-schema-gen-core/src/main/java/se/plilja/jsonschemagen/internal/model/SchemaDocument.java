package se.plilja.jsonschemagen.internal.model;

import java.util.Map;

/**
 * A parsed JSON Schema document: the root schema together with all
 * {@code $ref} targets resolved against the document.
 */
public final class SchemaDocument {

    private final Schema root;

    /**
     * Refs keyed by their original string form (e.g. {@code "#"},
     * {@code "#/definitions/Address"}). The same {@link Schema} instance is shared
     * across multiple refs that point at the same definition, so generator phase
     * state is shared.
     */
    private final Map<String, Schema> refs;

    public SchemaDocument(Schema root, Map<String, Schema> refs) {
        this.root = root;
        this.refs = Map.copyOf(refs);
    }

    public Schema getRoot() {
        return root;
    }

    public Schema resolveRef(String ref) {
        return refs.get(ref);
    }
}
