package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;
import static se.plilja.jsonschemagen.internal.generator.GenerationResult.skip;
import static se.plilja.jsonschemagen.internal.util.FunctionalUtil.coalesce;

import com.github.curiousoddman.rgxgen.RgxGen;
import com.github.curiousoddman.rgxgen.config.RgxGenOption;
import com.github.curiousoddman.rgxgen.config.RgxGenProperties;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.StringSchema;
import se.plilja.jsonschemagen.internal.util.RandomUtil;

/**
 * Generator for {@code "type": "string"} schemas without a recognised
 * {@code format}. Respects {@code minLength}, {@code maxLength}, and
 * {@code pattern} constraints.
 */
final class StringGenerator extends PhaseGenerator<StringGenerator.GenerationPhase, String> {

    private static final int PATTERN_RETRY_BUDGET = 100;

    private final StringSchema schema;
    private final RgxGen rgxGen;

    enum GenerationPhase {
        MIN_LENGTH, MAX_LENGTH, EMPTY, RANDOM
    }

    StringGenerator(GeneratorContext context, StringSchema schema) {
        super(GenerationPhase.class, context);
        this.schema = schema;
        this.rgxGen = schema.getPattern() != null ? buildRgxGen(schema) : null;
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.RANDOM;
    }

    private static RgxGen buildRgxGen(StringSchema schema) {
        // Cap unbounded quantifier expansion at maxLength so most generations land within bounds.
        if (schema.getMaxLength() == null) {
            return RgxGen.parse(schema.getPattern());
        }
        var properties = new RgxGenProperties();
        RgxGenOption.INFINITE_PATTERN_REPETITION.setInProperties(properties, schema.getMaxLength());
        return RgxGen.parse(properties, schema.getPattern());
    }

    @Override
    protected GenerationResult<String> generatePhase(GenerationPhase phase) {
        if (rgxGen != null) {
            return switch (phase) {
                case MIN_LENGTH -> schema.getMinLength() != null ? generateFromPatternWithLength(schema.getMinLength()) : skip();
                case MAX_LENGTH -> schema.getMaxLength() != null ? generateFromPatternWithLength(schema.getMaxLength()) : skip();
                case EMPTY -> {
                    int min = coalesce(schema.getMinLength(), 0);
                    yield min == 0 ? generateFromPatternWithLength(0) : skip();
                }
                case RANDOM -> result(generateFromPattern());
            };
        }
        return switch (phase) {
            case MIN_LENGTH -> schema.getMinLength() != null ? result(RandomUtil.randomStringOfLength(schema.getMinLength(), context.random())) : skip();
            case MAX_LENGTH -> schema.getMaxLength() != null ? result(RandomUtil.randomStringOfLength(schema.getMaxLength(), context.random())) : skip();
            case EMPTY -> {
                int min = coalesce(schema.getMinLength(), 0);
                yield min == 0 ? result("") : skip();
            }
            case RANDOM -> result(randomString());
        };
    }

    private GenerationResult<String> generateFromPatternWithLength(int targetLength) {
        for (int attempt = 0; attempt < PATTERN_RETRY_BUDGET; attempt++) {
            var candidate = rgxGen.generate(context.random());
            if (candidate.length() == targetLength) {
                return result(candidate);
            }
        }
        return skip();
    }

    private String generateFromPattern() {
        int min = coalesce(schema.getMinLength(), 0);
        int max = coalesce(schema.getMaxLength(), Integer.MAX_VALUE);
        for (int attempt = 0; attempt < PATTERN_RETRY_BUDGET; attempt++) {
            var candidate = rgxGen.generate(context.random());
            if (candidate.length() >= min && candidate.length() <= max) {
                return candidate;
            }
        }
        throw new UnsatisfiableSchemaException(
                "Not able to generate a string matching pattern '" + schema.getPattern()
                        + "' within length bounds [" + min + ", " + max + "]");
    }

    private String randomString() {
        int min = coalesce(schema.getMinLength(), 0);
        int max = coalesce(schema.getMaxLength(), min + 20);
        int length = min == max ? min : context.random().nextInt(min, max + 1);
        return RandomUtil.randomStringOfLength(length, context.random());
    }
}
