package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.GenerationResult.result;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.generator.GenerationResult;
import io.github.gjuton.internal.generator.GeneratorContext;
import io.github.gjuton.internal.model.StringSchema;
import java.util.UUID;

/**
 * Generates lowercase UUID strings for the {@code uuid} format.
 */
public final class UuidGenerator extends StringFormatGenerator<UuidGenerator.UuidPhase> {

    private static final int UUID_LENGTH = 36;

    protected enum UuidPhase {
        RANDOM
    }

    public UuidGenerator(GeneratorContext context, StringSchema schema) {
        super(UuidPhase.class, context, schema);
    }

    @Override
    protected UuidPhase minimalPhase() {
        return UuidPhase.RANDOM;
    }

    @Override
    protected GenerationResult<String> generatePhase(UuidPhase phase) {
        if (schema.getMinLength() != null && schema.getMinLength() > UUID_LENGTH
                || schema.getMaxLength() != null && schema.getMaxLength() < UUID_LENGTH) {
            throw new UnsatisfiableSchemaException(
                    "UUIDs are fixed at " + UUID_LENGTH + " characters; schema length bounds exclude that");
        }
        return result(randomWithRetry());
    }

    @Override
    protected String generateCandidate() {
        var bytes = new byte[16];
        context.random().nextBytes(bytes);
        return UUID.nameUUIDFromBytes(bytes).toString();
    }
}
