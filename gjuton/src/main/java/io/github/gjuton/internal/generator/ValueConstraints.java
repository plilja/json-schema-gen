package io.github.gjuton.internal.generator;

import io.github.gjuton.internal.util.RandomUtil;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;

/**
 * Effective bounds for a generation run, threaded into the generator tree as
 * part of {@link GeneratorConfig}. Every field is non-null; generators use
 * the values directly without null-checking.
 *
 * <p>Two factory methods supply mode-specific defaults:
 * {@link #forRandom()} produces narrow, realistic-looking bounds (dates near
 * the current year, moderate number range), while {@link #forExhaustive()}
 * produces wide bounds that leave the full schema range reachable.
 * {@link io.github.gjuton.api.Gjuton} overlays any caller-supplied
 * constraints onto the appropriate base before threading the result into the
 * generator tree.
 *
 * <p>For string and array maximum lengths the sentinel
 * {@link Integer#MAX_VALUE} means "no cap"; generators that apply an
 * offset-based fallback (e.g. {@code minLength + 20}) treat it as unbounded.
 */
public record ValueConstraints(
        int stringMinLength,
        int stringMaxLength,
        BigDecimal numberMin,
        BigDecimal numberMax,
        Instant dateMin,
        Instant dateMax,
        String alphabet,
        int arrayMinLength,
        int arrayMaxLength) {

    private static final Instant EXHAUSTIVE_DATE_MIN = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant EXHAUSTIVE_DATE_MAX = Instant.parse("2099-12-31T23:59:59Z");

    private static final BigDecimal RANDOM_NUMBER_MIN = BigDecimal.valueOf(-1_000_000);
    private static final BigDecimal RANDOM_NUMBER_MAX = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal EXHAUSTIVE_NUMBER_MIN = BigDecimal.valueOf(-Long.MAX_VALUE);
    private static final BigDecimal EXHAUSTIVE_NUMBER_MAX = BigDecimal.valueOf(Long.MAX_VALUE);

    /**
     * Defaults for {@link io.github.gjuton.api.GenerationMode#RANDOM}: dates
     * span the previous year through the next year, numbers stay within
     * &plusmn;1&thinsp;000&thinsp;000, and string/array lengths are unbounded
     * (generators apply their own offset-based caps).
     */
    public static ValueConstraints forRandom() {
        int thisYear = Year.now(ZoneOffset.UTC).getValue();
        var dateMin = Year.of(thisYear - 1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var dateMax = Year.of(thisYear + 1).atMonth(12).atEndOfMonth()
                .atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
        return new ValueConstraints(
                0,
                Integer.MAX_VALUE,
                RANDOM_NUMBER_MIN,
                RANDOM_NUMBER_MAX,
                dateMin,
                dateMax,
                RandomUtil.ENGLISH_ALPHABET,
                0,
                Integer.MAX_VALUE);
    }

    /**
     * Defaults for {@link io.github.gjuton.api.GenerationMode#EXHAUSTIVE}:
     * wide bounds that leave boundary-value phases room to reach schema
     * extremes.
     */
    public static ValueConstraints forExhaustive() {
        return new ValueConstraints(
                0,
                Integer.MAX_VALUE,
                EXHAUSTIVE_NUMBER_MIN,
                EXHAUSTIVE_NUMBER_MAX,
                EXHAUSTIVE_DATE_MIN,
                EXHAUSTIVE_DATE_MAX,
                RandomUtil.ENGLISH_ALPHABET,
                0,
                Integer.MAX_VALUE);
    }
}
