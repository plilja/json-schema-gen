package se.plilja.jsonschemagen.internal.generator.format;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;
import static se.plilja.jsonschemagen.internal.util.FunctionalUtil.coalesce;

import java.util.List;
import se.plilja.jsonschemagen.internal.generator.GenerationResult;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.model.StringSchema;
import se.plilja.jsonschemagen.internal.util.RandomUtil;

/**
 * Generates valid ECMA-262 regular expression strings.
 *
 * <p>Values are drawn from a curated pool of realistic patterns rather than
 * synthesized from random regex syntax, guaranteeing that every generated
 * value is a syntactically valid regular expression. The SHORT phase emits
 * the shortest valid regex ({@code "."}), the LONG phase pads with {@code "."}
 * repetitions to reach a target length, and the RANDOM phase picks from
 * the full pool.
 */
public final class RegexGenerator extends StringFormatGenerator<RegexGenerator.RegexPhase> {

    private static final int DEFAULT_LONG_LENGTH = 30;

    private static final List<String> PATTERNS = List.of(
            ".*",
            ".",
            "\\d+",
            "[a-z]+",
            "^\\S+@\\S+\\.\\S+$",
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            "^4[0-9]{12}(?:[0-9]{3})?$",
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
            "^https?://",
            ".?",
            "^[a-z0-9]+$",
            "\\w+"
    );

    enum RegexPhase {
        SHORT, LONG, RANDOM
    }

    public RegexGenerator(GeneratorContext context, StringSchema schema) {
        super(RegexPhase.class, context, schema);
    }

    @Override
    protected RegexPhase minimalPhase() {
        return RegexPhase.SHORT;
    }

    @Override
    protected GenerationResult<String> generatePhase(RegexPhase phase) {
        return switch (phase) {
            case SHORT -> tryCandidate(shortRegex());
            case LONG -> tryCandidate(longRegex());
            case RANDOM -> result(randomWithRetry());
        };
    }

    @Override
    protected String generateCandidate() {
        return RandomUtil.randomOne(PATTERNS, context.random());
    }

    private String shortRegex() {
        int target = coalesce(schema.getMinLength(), 1);
        return ".".repeat(target);
    }

    private String longRegex() {
        int target = coalesce(schema.getMaxLength(), DEFAULT_LONG_LENGTH);
        return ".".repeat(Math.max(1, target));
    }
}
