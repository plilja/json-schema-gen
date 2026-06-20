package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;
import static se.plilja.jsonschemagen.internal.generator.GenerationResult.skip;

import se.plilja.jsonschemagen.internal.model.NumericSchema;

/**
 * Generator for {@code "type": "integer"} schemas. Note that JSON Schema
 * integers are arbitrary-precision whole numbers, not bounded like Java's
 * {@code int} or {@code long}; this generator uses {@code long} as its
 * value type.
 */
final class NumericGenerator extends PhaseGenerator<NumericGenerator.GenerationPhase, Long> {

    // 2^53 - 1: above this, multipleOf checks done in IEEE 754 double precision are unreliable.
    private static final long MAX_SAFE_INTEGER = (1L << 53) - 1;

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
    protected GenerationResult<Long> generatePhase(GenerationPhase phase) {
        long effMin = effectiveMin();
        long effMax = effectiveMax();
        long lowestMultiple = snapUp(effMin);
        long highestMultiple = snapDown(effMax);
        return switch (phase) {
            case MIN -> hasLowerBound() || hasMultipleOf() ? result(lowestMultiple) : skip();
            case MAX -> hasUpperBound() || hasMultipleOf() ? result(highestMultiple) : skip();
            case ZERO -> {
                if (!isInRange(0)) {
                    yield skip();
                }
                if (lowestMultiple == 0 || highestMultiple == 0) {
                    yield skip();
                }
                yield result(0L);
            }
            case NEAR_MIN -> {
                if (!hasLowerBound() && !hasMultipleOf()) {
                    yield skip();
                }
                long nearMin = lowestMultiple + step();
                yield isInRange(nearMin) ? result(nearMin) : skip();
            }
            case NEAR_MAX -> {
                if (!hasUpperBound() && !hasMultipleOf()) {
                    yield skip();
                }
                long nearMax = highestMultiple - step();
                yield isInRange(nearMax) ? result(nearMax) : skip();
            }
            case RANDOM -> result(randomLong());
        };
    }

    private long effectiveMin() {
        Long min = schema.getMinimum();
        Long exMin = schema.getExclusiveMinimum();
        if (min != null && exMin != null) {
            return Math.max(min, exMin + 1);
        } else if (exMin != null) {
            return exMin + 1;
        } else if (min != null) {
            return min;
        }
        return hasMultipleOf() ? -MAX_SAFE_INTEGER : Long.MIN_VALUE;
    }

    private long effectiveMax() {
        Long max = schema.getMaximum();
        Long exMax = schema.getExclusiveMaximum();
        if (max != null && exMax != null) {
            return Math.min(max, exMax - 1);
        } else if (exMax != null) {
            return exMax - 1;
        } else if (max != null) {
            return max;
        }
        return hasMultipleOf() ? MAX_SAFE_INTEGER : Long.MAX_VALUE - 1;
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

    private long step() {
        return hasMultipleOf() ? schema.getMultipleOf() : 1;
    }

    private long snapUp(long value) {
        if (!hasMultipleOf()) {
            return value;
        }
        long m = schema.getMultipleOf();
        return Math.ceilDiv(value, m) * m;
    }

    private long snapDown(long value) {
        if (!hasMultipleOf()) {
            return value;
        }
        long m = schema.getMultipleOf();
        return Math.floorDiv(value, m) * m;
    }

    private boolean isInRange(long value) {
        if (value < effectiveMin() || value > effectiveMax()) {
            return false;
        }
        if (hasMultipleOf() && value % schema.getMultipleOf() != 0) {
            return false;
        }
        return true;
    }

    private long randomLong() {
        long lowestMultiple = snapUp(effectiveMin());
        long highestMultiple = snapDown(effectiveMax());
        if (!hasMultipleOf()) {
            return context.random().nextLong(lowestMultiple, highestMultiple + 1);
        }
        long m = schema.getMultipleOf();
        long lowestIndex = lowestMultiple / m;
        long highestIndex = highestMultiple / m;
        return context.random().nextLong(lowestIndex, highestIndex + 1) * m;
    }
}
