package se.plilja.jsonschemagen.internal.generator;

final class FunctionalUtil {

    private FunctionalUtil() {
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
