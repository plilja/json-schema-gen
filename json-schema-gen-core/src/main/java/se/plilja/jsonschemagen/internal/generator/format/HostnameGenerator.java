package se.plilja.jsonschemagen.internal.generator.format;

import static se.plilja.jsonschemagen.internal.generator.FunctionalUtil.coalesce;
import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.List;
import java.util.Random;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.generator.GenerationResult;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.generator.RandomUtil;
import se.plilja.jsonschemagen.internal.generator.StringUtil;
import se.plilja.jsonschemagen.internal.model.StringSchema;

public final class HostnameGenerator extends StringFormatGenerator<HostnameGenerator.HostnamePhase> {

    public enum HostnamePhase {
        SHORT, LONG, RANDOM
    }

    private static final int MIN_LABELS = 2;
    private static final int MAX_LABELS = 4;
    private static final int MIN_RANDOM_LABEL_LEN = 1;
    private static final int MAX_RANDOM_LABEL_LEN = 8;
    private static final int MAX_LABEL_LEN = 63;

    private final Alphabet canonical;
    private final List<Alphabet> randomPool;

    public HostnameGenerator(GeneratorContext context, StringSchema schema) {
        this(context, schema, Alphabets.EN, List.of(Alphabets.EN));
    }

    HostnameGenerator(GeneratorContext context, StringSchema schema, Alphabet canonical, List<Alphabet> randomPool) {
        super(HostnamePhase.class, context, schema);
        this.canonical = canonical;
        this.randomPool = randomPool;
        int minReachable = randomPool.stream()
                .mapToInt(a -> MIN_RANDOM_LABEL_LEN + 1 + StringUtil.shortest(a.tlds()).length())
                .min().orElseThrow();
        int maxReachable = randomPool.stream()
                .mapToInt(a -> (MAX_LABELS - 1) * MAX_RANDOM_LABEL_LEN + (MAX_LABELS - 1) + StringUtil.longest(a.tlds()).length())
                .max().orElseThrow();
        if (schema.getMinLength() != null && schema.getMinLength() > maxReachable
                || schema.getMaxLength() != null && schema.getMaxLength() < minReachable) {
            throw new UnsatisfiableSchemaException(
                    "Hostnames produced by this generator are between " + minReachable
                            + " and " + maxReachable + " characters; schema length bounds exclude that");
        }
    }

    @Override
    protected HostnamePhase minimalPhase() {
        return HostnamePhase.SHORT;
    }

    @Override
    protected GenerationResult<String> generatePhase(HostnamePhase phase) {
        return switch (phase) {
            case SHORT -> tryCandidate(shortHostname());
            case LONG -> tryCandidate(longHostname());
            case RANDOM -> result(randomWithRetry());
        };
    }

    @Override
    protected String generateCandidate() {
        var alphabet = RandomUtil.randomOne(randomPool, context.random());
        return randomHostname(alphabet, context.random());
    }

    private static String randomHostname(Alphabet alphabet, Random random) {
        int labelCount = random.nextInt(MIN_LABELS, MAX_LABELS + 1);
        var sb = new StringBuilder();
        for (int i = 0; i < labelCount - 1; i++) {
            if (i > 0) {
                sb.append('.');
            }
            int len = random.nextInt(MIN_RANDOM_LABEL_LEN, MAX_RANDOM_LABEL_LEN + 1);
            sb.append(RandomUtil.randomStringOfLength(alphabet.chars(), len, random));
        }
        sb.append('.');
        sb.append(RandomUtil.randomOne(alphabet.tlds(), random));
        return sb.toString();
    }

    private String shortHostname() {
        var suffix = "." + StringUtil.shortest(canonical.tlds());
        int target = Math.max(1 + suffix.length(), coalesce(schema.getMinLength(), 0));
        int leadingLen = Math.min(MAX_LABEL_LEN, target - suffix.length());
        return RandomUtil.randomStringOfLength(canonical.chars(), leadingLen, context.random()) + suffix;
    }

    private String longHostname() {
        var suffix = "." + StringUtil.longest(canonical.tlds());
        int target = coalesce(schema.getMaxLength(), 30);
        int leadingLen = Math.max(1, Math.min(MAX_LABEL_LEN, target - suffix.length()));
        return RandomUtil.randomStringOfLength(canonical.chars(), leadingLen, context.random()) + suffix;
    }
}
