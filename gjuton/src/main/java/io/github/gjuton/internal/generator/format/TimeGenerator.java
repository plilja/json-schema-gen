package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.GenerationResult.result;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.generator.GenerationResult;
import io.github.gjuton.internal.generator.GeneratorContext;
import io.github.gjuton.internal.model.StringSchema;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generates RFC 3339 {@code full-time} strings (e.g. {@code 14:30:00Z})
 * for the {@code time} format.
 */
public final class TimeGenerator extends StringFormatGenerator<TimeGenerator.TimePhase> {

    // RFC 3339 time strings span "HH:MM:SSZ" (9 chars) through "HH:MM:SS±HH:MM" (14 chars).
    private static final int MIN_TIME_LENGTH = 9;
    private static final int MAX_TIME_LENGTH = 14;
    private static final int SECONDS_PER_DAY = 86400;
    // Force HH:mm:ss even when seconds==0 (RFC 3339 requires it; ISO_OFFSET_TIME would drop it).
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ssXXX");

    protected enum TimePhase {
        MIDNIGHT, RANDOM
    }

    public TimeGenerator(GeneratorContext context, StringSchema schema) {
        super(TimePhase.class, context, schema);
    }

    @Override
    protected TimePhase minimalPhase() {
        return TimePhase.RANDOM;
    }

    @Override
    protected GenerationResult<String> generatePhase(TimePhase phase) {
        if (schema.getMinLength() != null && schema.getMinLength() > MAX_TIME_LENGTH
                || schema.getMaxLength() != null && schema.getMaxLength() < MIN_TIME_LENGTH) {
            throw new UnsatisfiableSchemaException(
                    "RFC 3339 times are between " + MIN_TIME_LENGTH + " and " + MAX_TIME_LENGTH
                            + " characters; schema length bounds exclude that");
        }
        return switch (phase) {
            case MIDNIGHT -> tryCandidate("00:00:00Z");
            case RANDOM -> result(randomWithRetry());
        };
    }

    @Override
    protected String generateCandidate() {
        var time = LocalTime.ofSecondOfDay(context.random().nextInt(SECONDS_PER_DAY));
        return OffsetTime.of(time, ZoneOffset.UTC).format(FORMATTER);
    }
}
