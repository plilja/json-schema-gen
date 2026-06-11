package se.plilja.jsonschemagen.internal.generator.format;

import static se.plilja.jsonschemagen.internal.generator.FunctionalUtil.coalesce;
import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.UUID;
import java.util.regex.Pattern;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.generator.GenerationResult;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.generator.PhaseGenerator;
import se.plilja.jsonschemagen.internal.model.StringSchema;

public final class UuidGenerator extends PhaseGenerator<UuidGenerator.UuidPhase, String> {

    private static final int RETRY_BUDGET = 100;
    private static final int UUID_LENGTH = 36;

    private final StringSchema schema;
    private final Pattern compiledPattern;

    protected enum UuidPhase {
        RANDOM
    }

    public UuidGenerator(GeneratorContext context, StringSchema schema) {
        super(UuidPhase.class, context);
        this.schema = schema;
        this.compiledPattern = schema.getPattern() != null ? Pattern.compile(schema.getPattern()) : null;
    }

    @Override
    protected UuidPhase minimalPhase() {
        return UuidPhase.RANDOM;
    }

    @Override
    protected GenerationResult<String> generatePhase(UuidPhase phase) {
        int min = coalesce(schema.getMinLength(), 0);
        int max = coalesce(schema.getMaxLength(), Integer.MAX_VALUE);
        if (min > UUID_LENGTH || max < UUID_LENGTH) {
            throw new UnsatisfiableSchemaException(
                    "UUIDs are fixed at " + UUID_LENGTH + " characters; schema length bounds exclude that");
        }
        for (int attempt = 0; attempt < RETRY_BUDGET; attempt++) {
            var candidate = randomUuid();
            if (compiledPattern == null || compiledPattern.matcher(candidate).find()) {
                return result(candidate);
            }
        }
        throw new UnsatisfiableSchemaException(
                "Not able to generate a UUID satisfying the schema's pattern constraint");
    }

    private String randomUuid() {
        var bytes = new byte[16];
        context.random().nextBytes(bytes);
        return UUID.nameUUIDFromBytes(bytes).toString();
    }
}
