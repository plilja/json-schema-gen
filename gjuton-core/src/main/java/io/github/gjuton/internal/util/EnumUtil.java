package io.github.gjuton.internal.util;

/**
 * Utility methods for working with enums.
 */
public final class EnumUtil {

    private EnumUtil() {
    }

    /**
     * Returns the first declared constant of the given enum type.
     */
    public static <E extends Enum<E>> E first(Class<E> enumClass) {
        return enumClass.getEnumConstants()[0];
    }

    /**
     * Returns the last declared constant of the given enum type.
     */
    public static <E extends Enum<E>> E last(Class<E> enumClass) {
        E[] values = enumClass.getEnumConstants();
        return values[values.length - 1];
    }

    /**
     * Returns the next constant after {@code current}, or {@code current}
     * itself if it is already the last constant.
     */
    public static <E extends Enum<E>> E next(E current) {
        E[] values = current.getDeclaringClass().getEnumConstants();
        int next = Math.min(current.ordinal() + 1, values.length - 1);
        return values[next];
    }
}
