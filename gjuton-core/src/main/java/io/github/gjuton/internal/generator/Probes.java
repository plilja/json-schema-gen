package io.github.gjuton.internal.generator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A fixed, type-diverse set of candidate JSON values used to meet a
 * constraint by trial rather than construction. It covers every JSON type,
 * with empty and non-empty variants of strings, arrays, and objects, so a
 * constraint forbidding one particular value still leaves a candidate of a
 * compatible shape.
 */
final class Probes {

    /**
     * Sentinel returned by {@link #firstMatching} when no candidate
     * satisfies the predicate, distinct from a matching {@code null} value.
     */
    static final Object NO_MATCH = new Object();

    private static final List<Object> VALUES = Stream.of(
            // boolean
            Boolean.TRUE, Boolean.FALSE,
            // number — positive, zero, negative
            BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.valueOf(-1),
            // string — empty, non-empty
            "", "probe",
            // array — empty, non-empty
            List.of(), List.of("item"),
            // object — empty, non-empty
            Map.of(), Map.of("key", "value"),
            // null
            null)
            .toList();

    private Probes() {
    }

    /**
     * Returns the first candidate value satisfying {@code predicate}, or
     * {@link #NO_MATCH} if none does. The returned value may itself be
     * {@code null} (a valid JSON candidate), so callers must compare the
     * result against {@link #NO_MATCH} rather than {@code null}.
     */
    static Object firstMatching(Predicate<Object> predicate) {
        for (var value : VALUES) {
            if (predicate.test(value)) {
                return value;
            }
        }
        return NO_MATCH;
    }
}
