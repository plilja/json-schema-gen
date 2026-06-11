package se.plilja.jsonschemagen.internal.generator.format;

import static se.plilja.jsonschemagen.internal.generator.FunctionalUtil.coalesce;
import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;
import static se.plilja.jsonschemagen.internal.generator.GenerationResult.skip;

import java.util.regex.Pattern;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.generator.GenerationResult;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.generator.PhaseGenerator;
import se.plilja.jsonschemagen.internal.generator.StringUtil;
import se.plilja.jsonschemagen.internal.model.StringSchema;

public final class EmailGenerator extends PhaseGenerator<EmailGenerator.EmailPhase, String> {

    private static final int RETRY_BUDGET = 100;
    private static final String[] TLDS = {
            "com", "org", "net", "io", "co", "ai", "dev", "app",
            "info", "biz", "uk", "us", "de", "jp", "fr", "se",
    };

    private final StringSchema schema;
    private final Pattern compiledPattern;

    enum EmailPhase {
        SHORT, LONG, RANDOM
    }

    public EmailGenerator(GeneratorContext context, StringSchema schema) {
        super(EmailPhase.class, context);
        this.schema = schema;
        this.compiledPattern = schema.getPattern() != null ? Pattern.compile(schema.getPattern()) : null;
    }

    @Override
    protected EmailPhase minimalPhase() {
        // SHORT/LONG can skip when tight bounds reject the canonical; RANDOM is the only
        // non-skippable phase here, as required by the PhaseGenerator contract.
        return EmailPhase.RANDOM;
    }

    @Override
    protected GenerationResult<String> generatePhase(EmailPhase phase) {
        return switch (phase) {
            case SHORT -> tryEmail(shortEmail());
            case LONG -> tryEmail(longEmail());
            case RANDOM -> result(randomWithRetry());
        };
    }

    private GenerationResult<String> tryEmail(String candidate) {
        return acceptable(candidate) ? result(candidate) : skip();
    }

    private String shortEmail() {
        int target = Math.max(6, coalesce(schema.getMinLength(), 0));
        int localLen = Math.max(1, target - "@b.co".length());
        return "a".repeat(localLen) + "@b.co";
    }

    private String longEmail() {
        // 64 cap matches RFC 5321 local-part max so the value stays validator-strict.
        int target = coalesce(schema.getMaxLength(), 30);
        int localLen = Math.max(1, Math.min(64, target - "@example.com".length()));
        return "a".repeat(localLen) + "@example.com";
    }

    private String randomWithRetry() {
        for (int attempt = 0; attempt < RETRY_BUDGET; attempt++) {
            var candidate = randomEmail();
            if (acceptable(candidate)) {
                return candidate;
            }
        }
        // TODO include schema identity in the message so a failing sub-schema can be located inside
        //  a larger schema. Needs a project-wide strategy (e.g. Schema.toDebugString) — out of scope here.
        throw new UnsatisfiableSchemaException(
                "Not able to generate an email satisfying the schema's pattern and length constraints");
    }

    private String randomEmail() {
        int localLen = context.random().nextInt(1, 8);
        int domainLen = context.random().nextInt(1, 8);
        var tld = TLDS[context.random().nextInt(TLDS.length)];
        return StringUtil.randomStringOfLength(localLen, context.random())
                + "@" + StringUtil.randomStringOfLength(domainLen, context.random())
                + "." + tld;
    }

    private boolean acceptable(String candidate) {
        if (schema.getMinLength() != null && candidate.length() < schema.getMinLength()) {
            return false;
        }
        if (schema.getMaxLength() != null && candidate.length() > schema.getMaxLength()) {
            return false;
        }
        return compiledPattern == null || compiledPattern.matcher(candidate).find();
    }
}
