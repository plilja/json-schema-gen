package io.github.gjuton.internal.util;

import static io.github.gjuton.internal.util.FunctionalUtil.coalesce;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

/**
 * Numeric utilities.
 */
public final class MathUtil {

    private MathUtil() {
    }

    /**
     * Inclusive integer range {@code [min, max]} — both endpoints are reachable.
     */
    public record IntRange(int min, int max) {
        public IntRange {
            if (min > max) {
                throw new IllegalArgumentException("min (" + min + ") must not exceed max (" + max + ")");
            }
        }

        public int pickRandom(Random random) {
            return random.nextInt(min, max + 1);
        }
    }

    /**
     * Clamps the input range {@code [min, max]} into the absolute range {@code [floor, ceiling]}.
     *
     * @throws IllegalArgumentException if the input range does not overlap {@code [floor, ceiling]}
     */
    public static IntRange clampRange(int min, int max, int floor, int ceiling) {
        if (max < floor || min > ceiling) {
            throw new IllegalArgumentException(
                    "input range [" + min + ", " + max + "] does not overlap [" + floor + ", " + ceiling + "]");
        }
        int low = Math.min(ceiling, Math.max(floor, min));
        int high = Math.min(ceiling, Math.max(low, max));
        return new IntRange(low, high);
    }

    static <T extends Comparable<T>> T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    static <T extends Comparable<T>> T min(T a, T b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /**
     * Returns the greater of {@code a} and {@code b}, treating {@code null}
     * as absent (not as a bound).
     */
    public static <T extends Comparable<T>> T maxNullable(T a, T b) {
        if (a == null || b == null) {
            return coalesce(a, b);
        }
        return max(a, b);
    }

    /**
     * Returns the lesser of {@code a} and {@code b}, treating {@code null}
     * as absent (not as a bound).
     */
    public static <T extends Comparable<T>> T minNullable(T a, T b) {
        if (a == null || b == null) {
            return coalesce(a, b);
        }
        return min(a, b);
    }

    static long gcd(long a, long b) {
        long x = Math.abs(a);
        long y = Math.abs(b);
        while (y != 0) {
            long t = y;
            y = x % y;
            x = t;
        }
        return x;
    }

    static long lcm(long a, long b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return Math.multiplyExact(Math.abs(a) / gcd(a, b), Math.abs(b));
    }

    /**
     * Returns the LCM of {@code a} and {@code b}, treating {@code null}
     * as absent (returns the other operand).
     */
    public static Long lcmNullable(Long a, Long b) {
        if (a == null || b == null) {
            return a == null ? b : a;
        }
        return lcm(a, b);
    }

    /**
     * Returns the LCM of two {@link BigDecimal} values by scaling them
     * to integers, computing the integer LCM, and scaling back.
     * Treats {@code null} as absent (returns the other operand).
     */
    public static BigDecimal lcmNullable(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null ? b : a;
        }
        int scale = Math.max(a.scale(), b.scale());
        var scaledFirst = a.movePointRight(scale).toBigIntegerExact();
        var scaledSecond = b.movePointRight(scale).toBigIntegerExact();
        var result = lcmBigInteger(scaledFirst, scaledSecond);
        return new BigDecimal(result, scale);
    }

    private static BigInteger lcmBigInteger(BigInteger a, BigInteger b) {
        if (a.signum() == 0 || b.signum() == 0) {
            return BigInteger.ZERO;
        }
        return a.abs().divide(a.gcd(b)).multiply(b.abs());
    }
}
