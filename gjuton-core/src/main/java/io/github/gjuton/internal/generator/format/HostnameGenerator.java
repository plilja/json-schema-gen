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

/**
 * Generates DNS hostname strings for the {@code hostname} format (RFC 952/1123).
 */
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

    static String randomHostname(Alphabet alphabet, Random random) {
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

    static String randomHostname(Alphabet alphabet, Random random, int length) {
        if (length < minReachable(alphabet)) {
            throw new IllegalArgumentException(
                    "Cannot build a hostname of length " + length + " (min reachable for alphabet: "
                            + minReachable(alphabet) + ")");
        }
        int spaceBeforeTld = length - 1;
        var fittingTlds = alphabet.tlds().stream()
                .filter(t -> spaceBeforeTld - t.length() >= MIN_RANDOM_LABEL_LEN)
                .toList();
        var tld = RandomUtil.randomOne(fittingTlds, random);
        var sb = new StringBuilder(length);
        int remaining = spaceBeforeTld - tld.length();
        while (remaining > 0) {
            int sep = sb.length() > 0 ? 1 : 0;
            int budget = remaining - sep;
            int maxLen = Math.min(MAX_LABEL_LEN, budget);
            int labelLen;
            if (maxLen <= 2) {
                labelLen = maxLen;
            } else {
                int forbidden = budget - 1;
                do {
                    labelLen = random.nextInt(1, maxLen + 1);
                } while (labelLen == forbidden);
            }
            if (sep > 0) {
                sb.append('.');
            }
            sb.append(RandomUtil.randomStringOfLength(alphabet.chars(), labelLen, random));
            remaining -= labelLen + sep;
        }
        sb.append('.').append(tld);
        return sb.toString();
    }

    static int minReachable(Alphabet alphabet) {
        return MIN_RANDOM_LABEL_LEN + 1 + StringUtil.shortest(alphabet.tlds()).length();
    }

    private String shortHostname() {
        int target = Math.max(minReachable(canonical), coalesce(schema.getMinLength(), 0));
        return randomHostname(canonical, context.random(), target);
    }

    private String longHostname() {
        int target = Math.max(minReachable(canonical), coalesce(schema.getMaxLength(), 30));
        return randomHostname(canonical, context.random(), target);
    }
}
