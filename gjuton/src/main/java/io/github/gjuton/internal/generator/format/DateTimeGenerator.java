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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generates RFC 3339 {@code date-time} strings (e.g.
 * {@code 2025-01-15T14:30:00Z}) for the {@code date-time} format.
 */
public final class DateTimeGenerator extends StringFormatGenerator<DateTimeGenerator.DateTimePhase> {

    // RFC 3339 date-time strings span "YYYY-MM-DDTHH:MM:SSZ" (20 chars) through "YYYY-MM-DDTHH:MM:SS±HH:MM" (25 chars).
    private static final int MIN_DATE_TIME_LENGTH = 20;
    private static final int MAX_DATE_TIME_LENGTH = 25;

    // 2038-01-19T03:14:07Z is the last second representable in a signed 32-bit time_t.
    // Systems that propagate values through 32-bit time arithmetic overflow one second later,
    // so emitting this exact instant exposes that latent bug class in any downstream consumer.
    private static final String Y2038_LAST_SECOND = "2038-01-19T03:14:07Z";
    private static final String MIDNIGHT_START_OF_YEAR = "2025-01-01T00:00:00Z";

    // Force HH:mm:ss even when seconds==0 (RFC 3339 requires it; OffsetDateTime.toString drops it).
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private static final Instant DEFAULT_MIN_INSTANT = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant DEFAULT_MAX_INSTANT = Instant.parse("2099-12-31T23:59:59Z");

    protected enum DateTimePhase {
        Y2038, LEAP_DAY, MIDNIGHT, RANDOM
    }

    public DateTimeGenerator(GeneratorContext context, StringSchema schema) {
        super(DateTimePhase.class, context, schema);
    }

    @Override
    protected DateTimePhase minimalPhase() {
        return DateTimePhase.RANDOM;
    }

    @Override
    protected GenerationResult<String> generatePhase(DateTimePhase phase) {
        if (schema.getMinLength() != null && schema.getMinLength() > MAX_DATE_TIME_LENGTH
                || schema.getMaxLength() != null && schema.getMaxLength() < MIN_DATE_TIME_LENGTH) {
            throw new UnsatisfiableSchemaException(
                    "RFC 3339 date-times are between " + MIN_DATE_TIME_LENGTH + " and " + MAX_DATE_TIME_LENGTH
                            + " characters; schema length bounds exclude that");
        }
        return switch (phase) {
            case Y2038 -> withinRange(Instant.parse(Y2038_LAST_SECOND)) ? tryCandidate(Y2038_LAST_SECOND) : skip();
            case LEAP_DAY -> {
                var leapDay = DateUtil.leapDayInRange(rangeMin(), rangeMax());
                yield leapDay != null ? tryCandidate(leapDay + "T12:00:00Z") : skip();
            }
            case MIDNIGHT -> withinRange(Instant.parse(MIDNIGHT_START_OF_YEAR)) ? tryCandidate(MIDNIGHT_START_OF_YEAR) : skip();
            case RANDOM -> result(randomWithRetry());
        };
    }

    @Override
    protected String generateCandidate() {
        long minEpochSecond = rangeMin().getEpochSecond();
        long maxEpochSecond = rangeMax().getEpochSecond();
        long epochSecond = context.random().nextLong(minEpochSecond, maxEpochSecond + 1);
        var offset = context.random().nextBoolean()
                ? ZoneOffset.UTC
                : ZoneOffset.ofHours(context.random().nextInt(-12, 15));
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), offset).format(FORMATTER);
    }

    private Instant rangeMin() {
        return coalesce(context.constraints().dateMin(), DEFAULT_MIN_INSTANT);
    }

    private Instant rangeMax() {
        return coalesce(context.constraints().dateMax(), DEFAULT_MAX_INSTANT);
    }

    private boolean withinRange(Instant instant) {
        return !instant.isBefore(rangeMin()) && !instant.isAfter(rangeMax());
    }
}
