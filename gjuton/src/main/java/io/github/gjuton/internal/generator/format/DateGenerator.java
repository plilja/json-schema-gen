package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.GenerationResult.result;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.generator.GenerationResult;
import io.github.gjuton.internal.generator.GeneratorContext;
import io.github.gjuton.internal.model.StringSchema;
import java.time.LocalDate;

/**
 * Generates RFC 3339 {@code full-date} strings ({@code YYYY-MM-DD}) for
 * the {@code date} format.
 */
public final class DateGenerator extends StringFormatGenerator<DateGenerator.DatePhase> {

    private static final int DATE_LENGTH = 10;
    private static final long MIN_EPOCH_DAY = LocalDate.of(1900, 1, 1).toEpochDay();
    private static final long MAX_EPOCH_DAY = LocalDate.of(2099, 12, 31).toEpochDay();

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
            case LEAP_DAY -> tryCandidate("2024-02-29");
            case RANDOM -> result(randomWithRetry());
        };
    }

    @Override
    protected String generateCandidate() {
        long epochDay = context.random().nextLong(MIN_EPOCH_DAY, MAX_EPOCH_DAY + 1);
        return LocalDate.ofEpochDay(epochDay).toString();
    }
}
