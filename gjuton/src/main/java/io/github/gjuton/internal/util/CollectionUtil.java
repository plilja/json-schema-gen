package io.github.gjuton.internal.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collection utilities.
 */
public final class CollectionUtil {

    private CollectionUtil() {
    }

    /**
     * Returns a new list containing all elements from both lists.
     * If one is {@code null}, returns a copy of the other.
     * If both are {@code null}, returns {@code null}.
     */
    public static <T> List<T> concat(List<T> a, List<T> b) {
        if (a == null) {
            return b == null ? null : List.copyOf(b);
        }
        if (b == null) {
            return List.copyOf(a);
        }
        var result = new ArrayList<>(a);
        result.addAll(b);
        return List.copyOf(result);
    }

    /**
     * Returns a new list with elements in reverse order.
     */
    public static <T> List<T> reversed(List<T> list) {
        var result = new ArrayList<>(list);
        Collections.reverse(result);
        return result;
    }
}
