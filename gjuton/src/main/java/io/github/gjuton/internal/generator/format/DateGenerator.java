package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.GenerationResult.result;
import static io.github.gjuton.internal.generator.GenerationResult.skip;
import static io.github.gjuton.internal.util.FunctionalUtil.coalesce;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.generator.GenerationResult;
import io.github.gjuton.internal.generator.GeneratorContext;
import io.github.gjuton.internal.model.StringSchema;
import io.github.gjuton.internal.util.DateUtil;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Generates RFC 3339 {@code full-date} strings ({@code YYYY-MM-DD}) for
 * the {@code date} format.
 */
public final class DateGenerator extends StringFormatGenerator<DateGenerator.DatePhase> {

    private static final int DATE_LENGTH = 10;

    private static final Instant DEFAULT_MIN_INSTANT = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant DEFAULT_MAX_INSTANT = Instant.parse("2099-12-31T23:59:59Z");

    protected enum DatePhase {
        LEAP_DAY, RANDOM
    }

    public DateGenerator(GeneratorContext context, StringSchema schema) {
        super(DatePhase.class, context, schema);
    }

    @Override
    protected DatePhase minimalPhase() {
        return DatePhase.RANDOM;
    }

    @Override
    protected GenerationResult<String> generatePhase(DatePhase phase) {
        if (schema.getMinLength() != null && schema.getMinLength() > DATE_LENGTH
                || schema.getMaxLength() != null && schema.getMaxLength() < DATE_LENGTH) {
            throw new UnsatisfiableSchemaException(
                    "RFC 3339 dates are fixed at " + DATE_LENGTH + " characters; schema length bounds exclude that");
        }
        return switch (phase) {
            case LEAP_DAY -> {
                var min = coalesce(context.constraints().dateMin(), DEFAULT_MIN_INSTANT);
                var max = coalesce(context.constraints().dateMax(), DEFAULT_MAX_INSTANT);
                var leapDay = DateUtil.leapDayInRange(min, max);
                yield leapDay != null ? tryCandidate(leapDay.toString()) : skip();
            }
            case RANDOM -> result(randomWithRetry());
        };
    }

    @Override
    protected String generateCandidate() {
        long minEpochDay = rangeMin().toEpochDay();
        long maxEpochDay = rangeMax().toEpochDay();
        long epochDay = context.random().nextLong(minEpochDay, maxEpochDay + 1);
        return LocalDate.ofEpochDay(epochDay).toString();
    }

    private LocalDate rangeMin() {
        var min = coalesce(context.constraints().dateMin(), DEFAULT_MIN_INSTANT);
        return LocalDate.ofInstant(min, ZoneOffset.UTC);
    }

    private LocalDate rangeMax() {
        var max = coalesce(context.constraints().dateMax(), DEFAULT_MAX_INSTANT);
        return LocalDate.ofInstant(max, ZoneOffset.UTC);
    }

}
