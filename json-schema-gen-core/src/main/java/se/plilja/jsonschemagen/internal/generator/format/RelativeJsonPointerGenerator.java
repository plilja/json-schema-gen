package se.plilja.jsonschemagen.internal.generator.format;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.generator.GenerationResult;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.model.StringSchema;

/**
 * Generates Relative JSON Pointer strings.
 *
 * <p>A relative JSON pointer is a non-negative integer prefix followed by either
 * a JSON Pointer suffix (which may be empty) or the character {@code #}.
 * Examples: {@code "0"} (self), {@code "1/foo"}, {@code "0#"}, {@code "2/bar/0"}.
 */
public final class RelativeJsonPointerGenerator extends StringFormatGenerator<RelativeJsonPointerGenerator.RelativeJsonPointerPhase> {

    enum RelativeJsonPointerPhase {
        SELF, RANDOM
    }

    public RelativeJsonPointerGenerator(GeneratorContext context, StringSchema schema) {
        super(RelativeJsonPointerPhase.class, context, schema);
        if (schema.getMaxLength() != null && schema.getMaxLength() < 1) {
            throw new UnsatisfiableSchemaException(
                    "Relative JSON pointers are at least 1 character; schema maxLength excludes that");
        }
    }

    @Override
    protected RelativeJsonPointerPhase minimalPhase() {
        return RelativeJsonPointerPhase.RANDOM;
    }

    @Override
    protected GenerationResult<String> generatePhase(RelativeJsonPointerPhase phase) {
        return switch (phase) {
            case SELF -> tryCandidate("0");
            case RANDOM -> result(randomWithRetry());
        };
    }

    @Override
    protected String generateCandidate() {
        var prefix = context.random().nextInt(0, 10);
        if (context.random().nextBoolean()) {
            return prefix + "#";
        } else {
            int segments = context.random().nextInt(0, 4);
            return prefix + JsonPointerGenerator.randomPointer(context.random(), segments);
        }
    }
}
