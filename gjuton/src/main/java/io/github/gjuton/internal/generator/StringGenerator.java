package io.github.gjuton.internal.generator;

import static io.github.gjuton.internal.generator.GenerationResult.result;
import static io.github.gjuton.internal.generator.GenerationResult.skip;
import static io.github.gjuton.internal.util.FunctionalUtil.coalesce;

import com.github.curiousoddman.rgxgen.RgxGen;
import com.github.curiousoddman.rgxgen.config.RgxGenOption;
import com.github.curiousoddman.rgxgen.config.RgxGenProperties;
import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.StringSchema;
import io.github.gjuton.internal.util.MathUtil;
import io.github.gjuton.internal.util.RandomUtil;

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
        this.rgxGen = schema.getPattern() != null ? buildRgxGen(schema, effectiveMaxLength()) : null;
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.RANDOM;
    }

    private static RgxGen buildRgxGen(StringSchema schema, Integer maxLength) {
        // Cap unbounded quantifier expansion at maxLength so most generations land within bounds.
        if (maxLength == null) {
            return RgxGen.parse(schema.getPattern());
        }
        var properties = new RgxGenProperties();
        RgxGenOption.INFINITE_PATTERN_REPETITION.setInProperties(properties, maxLength);
        return RgxGen.parse(properties, schema.getPattern());
    }

    @Override
    protected GenerationResult<String> generatePhase(GenerationPhase phase) {
        int minLength = effectiveMinLength();
        Integer maxLength = effectiveMaxLength();
        if (maxLength != null && minLength > maxLength) {
            throw new UnsatisfiableSchemaException(
                    "String length bounds are empty after applying constraints: effective minimum " + minLength
                            + " exceeds effective maximum " + maxLength);
        }
        if (rgxGen != null) {
            return switch (phase) {
                case MIN_LENGTH -> hasLowerLengthBound() ? generateFromPatternWithLength(minLength) : skip();
                case MAX_LENGTH -> hasUpperLengthBound() ? generateFromPatternWithLength(maxLength) : skip();
                case EMPTY -> minLength == 0 ? generateFromPatternWithLength(0) : skip();
                case RANDOM -> result(generateFromPattern());
            };
        }
        return switch (phase) {
            case MIN_LENGTH -> hasLowerLengthBound() ? result(RandomUtil.randomStringOfLength(alphabet(), minLength, context.random())) : skip();
            case MAX_LENGTH -> hasUpperLengthBound() ? result(RandomUtil.randomStringOfLength(alphabet(), maxLength, context.random())) : skip();
            case EMPTY -> minLength == 0 ? result("") : skip();
            case RANDOM -> result(randomString());
        };
    }

    private int effectiveMinLength() {
        int schemaMin = coalesce(schema.getMinLength(), 0);
        Integer constraintMin = context.constraints().stringMinLength();
        return constraintMin != null ? Math.max(schemaMin, constraintMin) : schemaMin;
    }

    /**
     * The tightest upper length bound, or {@code null} when neither schema nor constraints cap it.
     */
    private Integer effectiveMaxLength() {
        Integer schemaMax = schema.getMaxLength();
        Integer constraintMax = context.constraints().stringMaxLength();
        return MathUtil.minNullable(schemaMax, constraintMax);
    }

    private boolean hasLowerLengthBound() {
        return schema.getMinLength() != null;
    }

    private boolean hasUpperLengthBound() {
        return schema.getMaxLength() != null;
    }

    private String alphabet() {
        return coalesce(context.constraints().alphabet(), RandomUtil.ENGLISH_ALPHABET);
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
        int min = effectiveMinLength();
        int max = coalesce(effectiveMaxLength(), Integer.MAX_VALUE);
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
        int min = effectiveMinLength();
        int max = coalesce(effectiveMaxLength(), min + 20);
        int length = min == max ? min : context.random().nextInt(min, max + 1);
        return RandomUtil.randomStringOfLength(alphabet(), length, context.random());
    }
}
