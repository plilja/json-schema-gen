package io.github.gjuton.internal.generator;

import static io.github.gjuton.internal.generator.GenerationResult.result;
import static io.github.gjuton.internal.generator.GenerationResult.skip;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.NumericSchema;
import io.github.gjuton.internal.util.MathUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Generator for {@code "type": "integer"} and {@code "type": "number"}
 * schemas. Integer schemas produce {@link Long} values; number schemas
 * produce {@link Double} values. Constraint arithmetic uses
 * {@link BigDecimal} internally for precision.
 */
final class NumericGenerator extends PhaseGenerator<NumericGenerator.GenerationPhase, Number> {

    private static final BigDecimal MAX_SAFE_INTEGER = new BigDecimal(1L << 53);
    private static final BigDecimal NEG_MAX_SAFE_INTEGER = MAX_SAFE_INTEGER.negate();
    private static final BigDecimal EPSILON = new BigDecimal("1E-20");

    private final NumericSchema schema;

    enum GenerationPhase {
        MIN,
        MAX,
        ZERO,
        NEAR_MIN,
        NEAR_MAX,
        RANDOM
    }

    NumericGenerator(GeneratorContext context, NumericSchema schema) {
        super(GenerationPhase.class, context);
        this.schema = schema;
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.RANDOM;
    }

    @Override
    protected GenerationResult<Number> generatePhase(GenerationPhase phase) {
        var effMin = effectiveMin();
        var effMax = effectiveMax();
        var lowestMultiple = snapUp(effMin);
        var highestMultiple = snapDown(effMax);
        return switch (phase) {
            case MIN -> (hasLowerBound() || hasMultipleOf()) ? resultIfValid(lowestMultiple) : skip();
            case MAX -> (hasUpperBound() || hasMultipleOf()) ? resultIfValid(highestMultiple) : skip();
            case ZERO -> resultIfValid(BigDecimal.ZERO);
            case NEAR_MIN -> {
                if (!hasLowerBound() && !hasMultipleOf()) {
                    yield skip();
                }
                yield resultIfValid(lowestMultiple.add(step()));
            }
            case NEAR_MAX -> {
                if (!hasUpperBound() && !hasMultipleOf()) {
                    yield skip();
                }
                yield resultIfValid(highestMultiple.subtract(step()));
            }
            case RANDOM -> result(toOutput(randomValue(lowestMultiple, highestMultiple)));
        };
    }

    private GenerationResult<Number> resultIfValid(BigDecimal value) {
        return isValid(value) ? result(toOutput(value)) : skip();
    }

    private Number toOutput(BigDecimal value) {
        if (schema.isInteger()) {
            return value.longValueExact();
        }
        return value.doubleValue();
    }

    private BigDecimal effectiveMin() {
        return schemaMin().max(context.constraints().numberMin());
    }

    private BigDecimal effectiveMax() {
        return schemaMax().min(context.constraints().numberMax());
    }

    private BigDecimal schemaMin() {
        var min = schema.getMinimum();
        var exMin = schema.getExclusiveMinimum();
        if (min != null && exMin != null) {
            var exMinAdj = makeExclusiveMinInclusive(exMin);
            return min.max(exMinAdj);
        } else if (exMin != null) {
            return makeExclusiveMinInclusive(exMin);
        } else if (min != null) {
            return min;
        }
        if (schema.isInteger()) {
            return hasMultipleOf() ? NEG_MAX_SAFE_INTEGER : new BigDecimal(Long.MIN_VALUE);
        }
        return hasMultipleOf() ? NEG_MAX_SAFE_INTEGER : BigDecimal.valueOf(-Double.MAX_VALUE);
    }

    private BigDecimal schemaMax() {
        var max = schema.getMaximum();
        var exMax = schema.getExclusiveMaximum();
        if (max != null && exMax != null) {
            var exMaxAdj = makeExclusiveMaxInclusive(exMax);
            return max.min(exMaxAdj);
        } else if (exMax != null) {
            return makeExclusiveMaxInclusive(exMax);
        } else if (max != null) {
            return max;
        }
        if (schema.isInteger()) {
            return hasMultipleOf() ? MAX_SAFE_INTEGER : new BigDecimal(Long.MAX_VALUE - 1);
        }
        return hasMultipleOf() ? MAX_SAFE_INTEGER : BigDecimal.valueOf(Double.MAX_VALUE);
    }

    /**
     * Converts an exclusive lower bound to the closest inclusive value.
     */
    private BigDecimal makeExclusiveMinInclusive(BigDecimal exMin) {
        if (schema.isInteger()) {
            return exMin.add(EPSILON);
        }
        return BigDecimal.valueOf(Math.nextUp(exMin.doubleValue()));
    }

    /**
     * Converts an exclusive upper bound to the closest inclusive value.
     */
    private BigDecimal makeExclusiveMaxInclusive(BigDecimal exMax) {
        if (schema.isInteger()) {
            return exMax.subtract(EPSILON);
        }
        return BigDecimal.valueOf(Math.nextDown(exMax.doubleValue()));
    }

    private boolean hasLowerBound() {
        return schema.getMinimum() != null || schema.getExclusiveMinimum() != null;
    }

    private boolean hasUpperBound() {
        return schema.getMaximum() != null || schema.getExclusiveMaximum() != null;
    }

    private boolean hasMultipleOf() {
        return schema.getMultipleOf() != null;
    }

    /**
     * Returns the effective grid step for integer schemas with a fractional
     * {@code multipleOf} — the smallest value that is both a multiple of the
     * constraint and an integer.
     */
    private BigDecimal effectiveMultipleOf() {
        var m = schema.getMultipleOf();
        if (schema.isInteger()) {
            // Widen to the nearest integer-valued multiple (e.g. 1.5 → 3)
            return MathUtil.lcmNullable(m, BigDecimal.ONE);
        }
        return m;
    }

    private BigDecimal step() {
        return hasMultipleOf() ? effectiveMultipleOf() : BigDecimal.ONE;
    }

    /**
     * Rounds {@code value} up to the nearest valid point — the next
     * integer for integer schemas, or the next multiple of
     * {@code multipleOf} when that constraint is set.
     */
    private BigDecimal snapUp(BigDecimal value) {
        if (!hasMultipleOf()) {
            if (schema.isInteger()) {
                return value.setScale(0, RoundingMode.CEILING);
            }
            return value;
        }
        var m = effectiveMultipleOf();
        var divided = value.divide(m, 0, RoundingMode.CEILING);
        return divided.multiply(m);
    }

    /**
     * Rounds {@code value} down to the nearest valid point — the previous
     * integer for integer schemas, or the previous multiple of
     * {@code multipleOf} when that constraint is set.
     */
    private BigDecimal snapDown(BigDecimal value) {
        if (!hasMultipleOf()) {
            if (schema.isInteger()) {
                return value.setScale(0, RoundingMode.FLOOR);
            }
            return value;
        }
        var m = effectiveMultipleOf();
        var divided = value.divide(m, 0, RoundingMode.FLOOR);
        return divided.multiply(m);
    }

    private boolean isValid(BigDecimal value) {
        if (value.compareTo(effectiveMin()) < 0 || value.compareTo(effectiveMax()) > 0) {
            return false;
        }
        if (schema.isInteger() && value.stripTrailingZeros().scale() > 0) {
            return false;
        }
        if (hasMultipleOf() && value.remainder(schema.getMultipleOf()).signum() != 0) {
            return false;
        }
        return true;
    }

    private BigDecimal randomValue(BigDecimal lowestMultiple, BigDecimal highestMultiple) {
        int comparison = lowestMultiple.compareTo(highestMultiple);
        if (comparison > 0) {
            throw new UnsatisfiableSchemaException(
                    "No valid value satisfies minimum/maximum/multipleOf together: effective lower bound "
                            + lowestMultiple + " exceeds effective upper bound " + highestMultiple);
        }
        if (comparison == 0) {
            return lowestMultiple;
        }
        if (!hasMultipleOf()) {
            if (schema.isInteger()) {
                long lo = lowestMultiple.longValueExact();
                long hi = highestMultiple.longValueExact();
                return BigDecimal.valueOf(context.random().nextLong(lo, hi + 1));
            } else {
                return BigDecimal.valueOf(context.random().nextDouble(lowestMultiple.doubleValue(), highestMultiple.doubleValue()));
            }
        }
        var m = effectiveMultipleOf();
        var lowestIndex = lowestMultiple.divide(m, 0, RoundingMode.UNNECESSARY);
        var highestIndex = highestMultiple.divide(m, 0, RoundingMode.UNNECESSARY);
        long randomIndex = context.random().nextLong(lowestIndex.longValueExact(), highestIndex.longValueExact() + 1);
        return BigDecimal.valueOf(randomIndex).multiply(m);
    }
}
