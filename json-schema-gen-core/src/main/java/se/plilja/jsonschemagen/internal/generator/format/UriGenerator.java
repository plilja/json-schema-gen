package se.plilja.jsonschemagen.internal.generator.format;

import static se.plilja.jsonschemagen.internal.generator.FunctionalUtil.coalesce;
import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.Random;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.generator.GenerationResult;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.generator.RandomUtil;
import se.plilja.jsonschemagen.internal.model.StringSchema;

public final class UriGenerator extends StringFormatGenerator<UriGenerator.UriPhase> {

    private static final int MIN_PATH_LEN = 2;
    private static final int MAX_PATH_LEN = 10;
    private static final int MIN_QUERY_KEY_LEN = 1;
    private static final int MAX_QUERY_KEY_LEN = 6;
    private static final String BARE_AUTHORITY = "http://a.co";
    private static final int MIN_REACHABLE = BARE_AUTHORITY.length();
    // Practical cap matching common URL field limits; keeps the unsatisfiable check finite.
    private static final int MAX_REACHABLE = 4096;
    private static final int DEFAULT_LONG_TARGET = 80;

    public enum UriPhase {
        SHORT, LONG, RANDOM
    }

    public UriGenerator(GeneratorContext context, StringSchema schema) {
        super(UriPhase.class, context, schema);
        if (schema.getMinLength() != null && schema.getMinLength() > MAX_REACHABLE
                || schema.getMaxLength() != null && schema.getMaxLength() < MIN_REACHABLE) {
            throw new UnsatisfiableSchemaException(
                    "URIs produced by this generator are between " + MIN_REACHABLE
                            + " and " + MAX_REACHABLE + " characters; schema length bounds exclude that");
        }
    }

    @Override
    protected UriPhase minimalPhase() {
        return UriPhase.SHORT;
    }

    @Override
    protected GenerationResult<String> generatePhase(UriPhase phase) {
        return switch (phase) {
            case SHORT -> tryCandidate(shortUri());
            case LONG -> tryCandidate(longUri());
            case RANDOM -> result(randomWithRetry());
        };
    }

    private String shortUri() {
        int target = coalesce(schema.getMinLength(), 0);
        if (target <= BARE_AUTHORITY.length()) {
            return BARE_AUTHORITY;
        }
        int pathLen = Math.max(1, target - BARE_AUTHORITY.length() - 1);
        return BARE_AUTHORITY + "/" + RandomUtil.randomStringOfLength(Alphabets.EN.chars(), pathLen, context.random());
    }

    private String longUri() {
        int target = Math.max(coalesce(schema.getMaxLength(), DEFAULT_LONG_TARGET),
                coalesce(schema.getMinLength(), 0));
        if (target <= BARE_AUTHORITY.length()) {
            return BARE_AUTHORITY;
        }
        int pathLen = Math.max(1, target - BARE_AUTHORITY.length() - 1);
        return BARE_AUTHORITY + "/" + RandomUtil.randomStringOfLength(Alphabets.EN.chars(), pathLen, context.random());
    }

    @Override
    protected String generateCandidate() {
        var random = context.random();
        return switch (random.nextInt(10)) {
            case 0 -> "telnet://" + Ipv4Generator.randomIpv4(random) + "/";
            case 1 -> "mailto:" + EmailGenerator.randomEmail(Alphabets.EN, random);
            case 2 -> webUri("http", random);
            default -> webUri("https", random);
        };
    }

    private static String webUri(String scheme, Random random) {
        var host = HostnameGenerator.randomHostname(Alphabets.EN, random);
        var path = RandomUtil.randomStringOfLength(Alphabets.EN.chars(),
                random.nextInt(MIN_PATH_LEN, MAX_PATH_LEN + 1), random);
        var key = RandomUtil.randomStringOfLength(Alphabets.EN.chars(),
                random.nextInt(MIN_QUERY_KEY_LEN, MAX_QUERY_KEY_LEN + 1), random);
        return scheme + "://" + host + "/" + path + "?" + key + "=" + random.nextInt(100);
    }
}
