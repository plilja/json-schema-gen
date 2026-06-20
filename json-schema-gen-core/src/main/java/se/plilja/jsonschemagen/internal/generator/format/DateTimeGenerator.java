package se.plilja.jsonschemagen.internal.generator.format;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.generator.GenerationResult;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.model.StringSchema;

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
    private static final String LEAP_DAY_NOON = "2024-02-29T12:00:00Z";
    private static final String MIDNIGHT_START_OF_YEAR = "2025-01-01T00:00:00Z";

    // Force HH:mm:ss even when seconds==0 (RFC 3339 requires it; OffsetDateTime.toString drops it).
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private static final long MIN_EPOCH_SECOND = LocalDate.of(1900, 1, 1).toEpochDay() * 86400L;
    private static final long MAX_EPOCH_SECOND = LocalDate.of(2099, 12, 31).toEpochDay() * 86400L + 86399L;

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
            case Y2038 -> tryCandidate(Y2038_LAST_SECOND);
            case LEAP_DAY -> tryCandidate(LEAP_DAY_NOON);
            case MIDNIGHT -> tryCandidate(MIDNIGHT_START_OF_YEAR);
            case RANDOM -> result(randomWithRetry());
        };
    }

    @Override
    protected String generateCandidate() {
        long epochSecond = context.random().nextLong(MIN_EPOCH_SECOND, MAX_EPOCH_SECOND + 1);
        var offset = context.random().nextBoolean()
                ? ZoneOffset.UTC
                : ZoneOffset.ofHours(context.random().nextInt(-12, 15));
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), offset).format(FORMATTER);
    }
}
