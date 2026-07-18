package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.GenerationResult.result;
import static io.github.gjuton.internal.util.FunctionalUtil.coalesce;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.generator.GenerationResult;
import io.github.gjuton.internal.generator.GeneratorContext;
import io.github.gjuton.internal.model.StringSchema;
import io.github.gjuton.internal.util.RandomUtil;
import io.github.gjuton.internal.util.StringUtil;
import java.util.List;
import java.util.Random;

public final class EmailGenerator extends StringFormatGenerator<EmailGenerator.EmailPhase> {

    private static final int MIN_LOCAL_LEN = 1;
    private static final int MAX_LOCAL_LEN = 7;
    private static final int MIN_DOMAIN_LEN = 1;
    private static final int MAX_DOMAIN_LEN = 7;
    private static final int LOCAL_LEN_RFC_CAP = 64;

    private final Alphabet canonical;
    private final List<Alphabet> randomPool;

    public enum EmailPhase {
        SHORT, LONG, RANDOM
    }

    public EmailGenerator(GeneratorContext context, StringSchema schema) {
        this(context, schema, Alphabets.EN, List.of(Alphabets.EN));
    }

    EmailGenerator(GeneratorContext context, StringSchema schema, Alphabet canonical, List<Alphabet> randomPool) {
        super(EmailPhase.class, context, schema);
        this.canonical = canonical;
        this.randomPool = randomPool;
        int minReachable = randomPool.stream()
                .mapToInt(a -> MIN_LOCAL_LEN + 1 + MIN_DOMAIN_LEN + 1 + StringUtil.shortest(a.tlds()).length())
                .min().orElseThrow();
        int maxReachable = randomPool.stream()
                .mapToInt(a -> LOCAL_LEN_RFC_CAP + 1 + 1 + 1 + StringUtil.longest(a.tlds()).length())
                .max().orElseThrow();
        if (schema.getMinLength() != null && schema.getMinLength() > maxReachable
                || schema.getMaxLength() != null && schema.getMaxLength() < minReachable) {
            throw new UnsatisfiableSchemaException(
                    "Email addresses produced by this generator are between " + minReachable
                            + " and " + maxReachable + " characters; schema length bounds exclude that");
        }
    }

    @Override
    protected EmailPhase minimalPhase() {
        return EmailPhase.SHORT;
    }

    @Override
    protected GenerationResult<String> generatePhase(EmailPhase phase) {
        return switch (phase) {
            case SHORT -> tryCandidate(shortEmail());
            case LONG -> tryCandidate(longEmail());
            case RANDOM -> result(randomWithRetry());
        };
    }

    @Override
    protected String generateCandidate() {
        var alphabet = RandomUtil.randomOne(randomPool, context.random());
        return randomEmail(alphabet, context.random());
    }

    static String randomEmail(Alphabet alphabet, Random random) {
        int localLen = random.nextInt(MIN_LOCAL_LEN, MAX_LOCAL_LEN + 1);
        int domainLen = random.nextInt(MIN_DOMAIN_LEN, MAX_DOMAIN_LEN + 1);
        var tld = RandomUtil.randomOne(alphabet.tlds(), random);
        return RandomUtil.randomStringOfLength(alphabet.chars(), localLen, random)
                + "@" + RandomUtil.randomStringOfLength(alphabet.chars(), domainLen, random)
                + "." + tld;
    }

    private String shortEmail() {
        var suffix = "@" + canonical.firstChar() + "." + StringUtil.shortest(canonical.tlds());
        int target = Math.max(MIN_LOCAL_LEN + suffix.length(), coalesce(schema.getMinLength(), 0));
        int localLen = Math.max(MIN_LOCAL_LEN, Math.min(LOCAL_LEN_RFC_CAP, target - suffix.length()));
        return canonical.firstChar().repeat(localLen) + suffix;
    }

    private String longEmail() {
        var suffix = "@" + canonical.firstChar() + "." + StringUtil.longest(canonical.tlds());
        int target = coalesce(schema.getMaxLength(), 30);
        int localLen = Math.max(MIN_LOCAL_LEN, Math.min(LOCAL_LEN_RFC_CAP, target - suffix.length()));
        return canonical.firstChar().repeat(localLen) + suffix;
    }
}
