package se.plilja.jsonschemagen.internal.util;

import java.util.Comparator;
import java.util.List;

/**
 * Utilities for working with strings.
 */
public final class StringUtil {

    private StringUtil() {
    }

    public static String shortest(List<String> strings) {
        return strings.stream().min(Comparator.comparingInt(String::length)).orElseThrow();
    }

    public static String longest(List<String> strings) {
        return strings.stream().max(Comparator.comparingInt(String::length)).orElseThrow();
    }
}
