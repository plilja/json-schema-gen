package io.github.gjuton.internal.util;

public final class FunctionalUtil {

    private FunctionalUtil() {
    }

    @SafeVarargs
    public static <T> T coalesce(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

}
