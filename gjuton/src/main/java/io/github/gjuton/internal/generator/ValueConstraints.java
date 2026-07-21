package io.github.gjuton.internal.generator;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Caller-imposed narrowing bounds threaded into the generator tree, the
 * internal counterpart of the public {@code Constraints}. Each field is
 * {@code null} when that kind is unset, meaning the schema and the generator's
 * own defaults govern it. Generators intersect a set field with the schema's
 * own constraint for that kind, keeping the tighter bound.
 */
public record ValueConstraints(
        Integer stringMinLength,
        Integer stringMaxLength,
        BigDecimal numberMin,
        BigDecimal numberMax,
        Instant dateMin,
        Instant dateMax,
        String alphabet,
        Integer arrayMinLength,
        Integer arrayMaxLength) {

    /**
     * Constraints that narrow nothing — every kind unset.
     */
    static ValueConstraints none() {
        return new ValueConstraints(null, null, null, null, null, null, null, null, null);
    }
}
