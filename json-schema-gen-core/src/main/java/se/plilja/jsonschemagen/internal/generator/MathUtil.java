package se.plilja.jsonschemagen.internal.generator;

final class MathUtil {

    private MathUtil() {
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

    static Long lcmNullable(Long a, Long b) {
        if (a == null || b == null) {
            return a == null ? b : a;
        }
        return lcm(a, b);
    }
}
