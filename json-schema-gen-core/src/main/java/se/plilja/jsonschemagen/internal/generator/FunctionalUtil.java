package se.plilja.jsonschemagen.internal.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class FunctionalUtil {

    private FunctionalUtil() {
    }

    static <T> List<T> randomSubset(List<T> items, int n, Random random) {
        var shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, n);
    }

    @SafeVarargs
    static <T> T coalesce(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static <T extends Comparable<T>> T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    static <T extends Comparable<T>> T min(T a, T b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    static <T extends Comparable<T>> T maxNullable(T a, T b) {
        if (a == null || b == null) {
            return coalesce(a, b);
        }
        return max(a, b);
    }

    static <T extends Comparable<T>> T minNullable(T a, T b) {
        if (a == null || b == null) {
            return coalesce(a, b);
        }
        return min(a, b);
    }
}
