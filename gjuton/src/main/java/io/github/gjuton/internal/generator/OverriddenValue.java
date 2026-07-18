package io.github.gjuton.internal.generator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps a caller-supplied value in the generated value tree to mark the
 * position as overridden. Two consumers treat a wrapped position specially:
 * {@link SchemaValidator} accepts it unconditionally (an override is exempt
 * from schema validation), and the final output pass unwraps it back to the
 * plain value.
 *
 * @param value the raw value the caller supplied
 */
record OverriddenValue(Object value) {

    /**
     * Returns a copy of {@code tree} with every {@link OverriddenValue} marker
     * replaced by the plain value it wraps, leaving a tree of ordinary maps,
     * lists, and scalars ready for serialization.
     */
    static Object strip(Object tree) {
        if (tree instanceof OverriddenValue overridden) {
            return overridden.value();
        }
        if (tree instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                result.put((String) entry.getKey(), strip(entry.getValue()));
            }
            return result;
        }
        if (tree instanceof List<?> list) {
            var result = new ArrayList<>(list.size());
            for (var element : list) {
                result.add(strip(element));
            }
            return result;
        }
        return tree;
    }
}
