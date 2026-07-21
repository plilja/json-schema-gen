package io.github.gjuton.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A set of caller-imposed bounds that narrow generated values beyond what the
 * schema requires, passed to {@link Gjuton#withConstraints(Constraints)}.
 * Instances are immutable; each setter returns a new instance, so constraints
 * build up fluently from {@link #of()}:
 *
 * <pre>{@code
 * Constraints.of()
 *         .stringLength(1, 40)
 *         .dateRange(Instant.parse("2000-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"));
 * }</pre>
 *
 * <p>Each bound only ever tightens: the effective range is the intersection of
 * the schema's own constraint and the matching bound here, so a looser bound has
 * no effect and an empty intersection fails generation as unsatisfiable. A kind
 * left unset imposes nothing; every setter requires both bounds.
 */
public final class Constraints {

    final Integer stringMinLength;
    final Integer stringMaxLength;
    final BigDecimal numberMin;
    final BigDecimal numberMax;
    final Instant dateMin;
    final Instant dateMax;
    final String alphabet;
    final Integer arrayMinLength;
    final Integer arrayMaxLength;

    private Constraints(
            Integer stringMinLength,
            Integer stringMaxLength,
            BigDecimal numberMin,
            BigDecimal numberMax,
            Instant dateMin,
            Instant dateMax,
            String alphabet,
            Integer arrayMinLength,
            Integer arrayMaxLength) {
        this.stringMinLength = stringMinLength;
        this.stringMaxLength = stringMaxLength;
        this.numberMin = numberMin;
        this.numberMax = numberMax;
        this.dateMin = dateMin;
        this.dateMax = dateMax;
        this.alphabet = alphabet;
        this.arrayMinLength = arrayMinLength;
        this.arrayMaxLength = arrayMaxLength;
    }

    /**
     * An empty set of constraints — the starting point every kind is added to.
     */
    public static Constraints of() {
        return new Constraints(null, null, null, null, null, null, null, null, null);
    }

    /**
     * Restricts the length of generated strings to {@code [min, max]} characters.
     * Has no effect on strings with a recognised {@code format} (such as
     * {@code email} or {@code uri}), whose length is dictated by the format.
     *
     * @throws IllegalArgumentException if a bound is negative, or {@code min > max}
     */
    public Constraints stringLength(int min, int max) {
        requireLengthOrder(min, max, "stringLength");
        return new Constraints(
                min, max, numberMin, numberMax, dateMin, dateMax, alphabet, arrayMinLength, arrayMaxLength);
    }

    /**
     * Restricts generated integers and numbers to {@code [min, max]}.
     *
     * @throws IllegalArgumentException if a bound is null or {@code min > max}
     */
    public Constraints numberRange(BigDecimal min, BigDecimal max) {
        if (min == null || max == null) {
            throw new IllegalArgumentException("numberRange requires both min and max; neither side may be null");
        }
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("numberRange min (" + min + ") must not exceed max (" + max + ")");
        }
        return new Constraints(
                stringMinLength, stringMaxLength, min, max, dateMin, dateMax, alphabet, arrayMinLength, arrayMaxLength);
    }

    /**
     * Restricts generated integers and numbers to {@code [min, max]}.
     *
     * @throws IllegalArgumentException if {@code min > max}
     */
    public Constraints numberRange(long min, long max) {
        return numberRange(BigDecimal.valueOf(min), BigDecimal.valueOf(max));
    }

    /**
     * Restricts generated integers and numbers to {@code [min, max]}.
     *
     * @throws IllegalArgumentException if {@code min > max}
     */
    public Constraints numberRange(double min, double max) {
        return numberRange(BigDecimal.valueOf(min), BigDecimal.valueOf(max));
    }

    /**
     * Restricts generated {@code date} and {@code date-time} values to the instant
     * range {@code [min, max]}. A generated {@code date-time} denotes an instant
     * within the range regardless of the offset it is rendered with; a
     * {@code date}, which has no time or offset, falls on a UTC calendar date
     * within the range. Overrides the default generation window. Both bounds are
     * required — unlike the other kinds, {@code dateRange} does not accept a
     * one-sided bound.
     *
     * @throws IllegalArgumentException if either bound is null, or {@code min} is after {@code max}
     */
    public Constraints dateRange(Instant min, Instant max) {
        if (min == null || max == null) {
            throw new IllegalArgumentException("dateRange requires both min and max; neither side may be null");
        }
        if (min.isAfter(max)) {
            throw new IllegalArgumentException("dateRange min (" + min + ") must not be after max (" + max + ")");
        }
        return new Constraints(
                stringMinLength, stringMaxLength, numberMin, numberMax, min, max, alphabet, arrayMinLength, arrayMaxLength);
    }

    /**
     * Restricts generated strings to draw only from the characters in
     * {@code alphabet}. Ignored for strings whose schema carries a
     * {@code pattern} (the pattern still governs those) or a recognised
     * {@code format} such as {@code email} or {@code uri} (the format dictates
     * the character set).
     *
     * @throws IllegalArgumentException if {@code alphabet} is null or empty
     */
    public Constraints alphabet(String alphabet) {
        if (alphabet == null || alphabet.isEmpty()) {
            throw new IllegalArgumentException("alphabet must not be null or empty");
        }
        return new Constraints(
                stringMinLength, stringMaxLength, numberMin, numberMax, dateMin, dateMax, alphabet, arrayMinLength, arrayMaxLength);
    }

    /**
     * Restricts the length of generated arrays to {@code [min, max]} elements.
     *
     * @throws IllegalArgumentException if a bound is negative, or {@code min > max}
     */
    public Constraints arrayLength(int min, int max) {
        requireLengthOrder(min, max, "arrayLength");
        return new Constraints(
                stringMinLength, stringMaxLength, numberMin, numberMax, dateMin, dateMax, alphabet, min, max);
    }

    private static void requireLengthOrder(int min, int max, String kind) {
        if (min < 0) {
            throw new IllegalArgumentException(kind + " min must not be negative, was " + min);
        }
        if (max < 0) {
            throw new IllegalArgumentException(kind + " max must not be negative, was " + max);
        }
        if (min > max) {
            throw new IllegalArgumentException(kind + " min (" + min + ") must not exceed max (" + max + ")");
        }
    }
}
