package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.GenerationResult.result;
import static io.github.gjuton.internal.generator.GenerationResult.skip;
import static io.github.gjuton.internal.util.FunctionalUtil.coalesce;
import static io.github.gjuton.internal.util.MathUtil.clampRange;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.generator.GenerationResult;
import io.github.gjuton.internal.generator.GeneratorContext;
import io.github.gjuton.internal.model.StringSchema;
import io.github.gjuton.internal.util.MathUtil;
import io.github.gjuton.internal.util.RandomUtil;
import java.util.List;
import java.util.Random;

/**
 * Emits values for the {@code uri-reference} format (RFC 3986 §4.1).
 *
 * <p>A URI reference is either an absolute URI ({@code scheme://...}) or a relative reference
 * (e.g. {@code a/b?q=1#frag}, or the empty string).
 */
public final class UriReferenceGenerator extends StringFormatGenerator<UriReferenceGenerator.UriReferencePhase> {

    static final int MAX_LENGTH = 4096;

    private static final int MAX_SEGMENT_LEN = 8;
    private static final int DEFAULT_LONG_TARGET = 80;
    private static final int HOSTNAME_TYPICAL_MAX = 30;

    public enum UriReferencePhase {
        EMPTY, RELATIVE, ABSOLUTE, RANDOM
    }

    private final List<Alphabet> alphabets;
    private final int minAbsolute;

    public UriReferenceGenerator(GeneratorContext context, StringSchema schema) {
        this(context, schema, List.of(Alphabets.EN));
    }

    UriReferenceGenerator(GeneratorContext context, StringSchema schema, List<Alphabet> alphabets) {
        super(UriReferencePhase.class, context, schema);
        this.alphabets = alphabets;
        this.minAbsolute = "http://".length() + alphabets.stream().mapToInt(HostnameGenerator::minReachable).min().orElseThrow();
        if (schema.getMinLength() != null && schema.getMinLength() > MAX_LENGTH) {
            throw new UnsatisfiableSchemaException(
                    "URI references produced by this generator cap at " + MAX_LENGTH
                            + " characters; schema length bounds exclude that");
        }
    }

    @Override
    protected UriReferencePhase minimalPhase() {
        return UriReferencePhase.EMPTY;
    }

    @Override
    protected GenerationResult<String> generatePhase(UriReferencePhase phase) {
        var alphabet = RandomUtil.randomOne(alphabets, context.random());
        return switch (phase) {
            case EMPTY -> tryCandidate("");
            case RELATIVE -> tryCandidate(randomRelativePath(schema, alphabet, context.random()));
            case ABSOLUTE -> randomAbsoluteUriOfLength();
            case RANDOM -> result(randomWithRetry());
        };
    }

    @Override
    protected String generateCandidate() {
        var random = context.random();
        var alphabet = RandomUtil.randomOne(alphabets, random);
        int minForAlphabet = "http://".length() + HostnameGenerator.minReachable(alphabet);
        boolean canAbsolute = coalesce(schema.getMaxLength(), DEFAULT_LONG_TARGET) >= minForAlphabet;
        if (canAbsolute && random.nextBoolean()) {
            return randomAbsoluteUri(schema, alphabet, random);
        }
        return randomRelativePath(schema, alphabet, random);
    }

    /**
     * Emits an absolute URI candidate for the ABSOLUTE phase, sized to the schema's maxLength
     * when set. Skips when maxLength is smaller than the shortest absolute URI we can produce.
     */
    private GenerationResult<String> randomAbsoluteUriOfLength() {
        if (coalesce(schema.getMaxLength(), DEFAULT_LONG_TARGET) < minAbsolute) {
            return skip();
        }
        var alphabet = RandomUtil.randomOne(alphabets, context.random());
        return tryCandidate(randomAbsoluteUriOfLength("http", uriLengthBounds(schema, minAbsolute).max(), alphabet, context.random()));
    }

    /**
     * Builds an absolute URI of exactly {@code target} characters: {@code scheme://host[/path]}.
     *
     * @throws IllegalArgumentException if {@code target} is shorter than the shortest reachable hostname
     */
    static String randomAbsoluteUriOfLength(String scheme, int target, Random random) {
        return randomAbsoluteUriOfLength(scheme, target, Alphabets.EN, random);
    }

    static String randomAbsoluteUriOfLength(String scheme, int target, Alphabet alphabet, Random random) {
        var prefix = scheme + "://";
        int budget = target - prefix.length();
        int minHostname = HostnameGenerator.minReachable(alphabet);
        if (budget < minHostname) {
            throw new IllegalArgumentException(
                    "target " + target + " too small for scheme '" + scheme
                            + "' (minimum " + (prefix.length() + minHostname) + ")");
        }
        int hostLen = pickHostLength(budget, minHostname, random);
        var host = HostnameGenerator.randomHostname(alphabet, random, hostLen);
        if (hostLen == budget) {
            return prefix + host;
        }
        int pathLen = budget - hostLen - 1;
        return prefix + host + "/" + randomRelativePath(pathLen, alphabet, random);
    }

    static String randomShortUri(StringSchema schema, Alphabet alphabet, Random random) {
        int minAbsolute = "http://".length() + HostnameGenerator.minReachable(alphabet);
        return randomAbsoluteUriOfLength("http", uriLengthBounds(schema, minAbsolute).min(), alphabet, random);
    }

    static String randomLongUri(StringSchema schema, Alphabet alphabet, Random random) {
        int minAbsolute = "http://".length() + HostnameGenerator.minReachable(alphabet);
        int minAbsoluteHttps = "https://".length() + HostnameGenerator.minReachable(alphabet);
        int length = uriLengthBounds(schema, minAbsolute).max();
        var scheme = length >= minAbsoluteHttps ? "https" : "http";
        return randomAbsoluteUriOfLength(scheme, length, alphabet, random);
    }

    static String randomAbsoluteUri(StringSchema schema, Alphabet alphabet, Random random) {
        int minAbsolute = "http://".length() + HostnameGenerator.minReachable(alphabet);
        int minAbsoluteHttps = "https://".length() + HostnameGenerator.minReachable(alphabet);
        int length = uriLengthBounds(schema, minAbsolute).pickRandom(random);
        var scheme = length >= minAbsoluteHttps && random.nextBoolean() ? "https" : "http";
        return randomAbsoluteUriOfLength(scheme, length, alphabet, random);
    }

    /**
     * Computes the effective length range for an absolute URI generated from {@code schema},
     * clamped to {@code [minAbsolute, MAX_LENGTH]}.
     */
    private static MathUtil.IntRange uriLengthBounds(StringSchema schema, int minAbsolute) {
        return clampRange(
                coalesce(schema.getMinLength(), minAbsolute),
                coalesce(schema.getMaxLength(), DEFAULT_LONG_TARGET),
                minAbsolute,
                MAX_LENGTH);
    }

    private static int pickHostLength(int budget, int minHostname, Random random) {
        if (budget <= minHostname + 1) {
            return minHostname;
        }
        boolean bareAuthority = budget <= HOSTNAME_TYPICAL_MAX && random.nextInt(4) == 0;
        if (bareAuthority) {
            return budget;
        }
        int maxHost = Math.min(HOSTNAME_TYPICAL_MAX, budget - 2);
        return random.nextInt(minHostname, maxHost + 1);
    }

    static String randomRelativePath(StringSchema schema, Alphabet alphabet, Random random) {
        var range = clampRange(
                coalesce(schema.getMinLength(), 0),
                coalesce(schema.getMaxLength(), MAX_LENGTH),
                0,
                MAX_LENGTH);
        return randomRelativePath(range.pickRandom(random), alphabet, random);
    }

    /**
     * Builds a relative path of exactly {@code length} characters.
     *
     * <p>For example {@code foo/bar/baz}.
     */
    static String randomRelativePath(int length, Alphabet alphabet, Random random) {
        if (length == 0) {
            return "";
        }
        var sb = new StringBuilder(length);
        while (sb.length() < length) {
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            int remaining = length - sb.length();
            int segLen = remaining <= MAX_SEGMENT_LEN
                    ? remaining
                    : random.nextInt(1, Math.min(MAX_SEGMENT_LEN, remaining - 2) + 1);
            sb.append(RandomUtil.randomStringOfLength(alphabet.chars(), segLen, random));
        }
        return sb.toString();
    }
}
