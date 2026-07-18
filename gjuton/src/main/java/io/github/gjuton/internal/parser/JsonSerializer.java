package io.github.gjuton.internal.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serializes the generator's in-memory value tree into a JSON string.
 */
public final class JsonSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Converts a value tree (maps, lists, scalars, nulls) to a compact JSON string.
     */
    public static String serialize(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize generated value", e);
        }
    }
}
