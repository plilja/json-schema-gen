package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.GenerationResult.result;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.generator.GenerationResult;
import io.github.gjuton.internal.generator.GeneratorContext;
import io.github.gjuton.internal.model.StringSchema;
import io.github.gjuton.internal.util.RandomUtil;
import java.util.List;

/**
 * Emits values for the {@code uri} format (RFC 3986 — absolute URIs only).
 */
public final class UriGenerator extends StringFormatGenerator<UriGenerator.UriPhase> {

    public enum UriPhase {
        SHORT, LONG, RANDOM
    }

    private final List<Alphabet> alphabets;

    public UriGenerator(GeneratorContext context, StringSchema schema) {
        this(context, schema, List.of(Alphabets.EN));
    }

    UriGenerator(GeneratorContext context, StringSchema schema, List<Alphabet> alphabets) {
        super(UriPhase.class, context, schema);
        this.alphabets = alphabets;
        int minAbsolute = "http://".length() + alphabets.stream().mapToInt(HostnameGenerator::minReachable).min().orElseThrow();
        if (schema.getMinLength() != null && schema.getMinLength() > UriReferenceGenerator.MAX_LENGTH) {
            throw new UnsatisfiableSchemaException(
                    "URIs produced by this generator cap at " + UriReferenceGenerator.MAX_LENGTH
                            + " characters; schema length bounds exclude that");
        }
        if (schema.getMaxLength() != null && schema.getMaxLength() < minAbsolute) {
            throw new UnsatisfiableSchemaException(
                    "URIs produced by this generator are at least " + minAbsolute
                            + " characters; schema maxLength excludes that");
        }
    }

    @Override
    protected UriPhase minimalPhase() {
        return UriPhase.SHORT;
    }

    @Override
    protected GenerationResult<String> generatePhase(UriPhase phase) {
        var alphabet = RandomUtil.randomOne(alphabets, context.random());
        return switch (phase) {
            case SHORT -> tryCandidate(UriReferenceGenerator.randomShortUri(schema, alphabet, context.random()));
            case LONG -> tryCandidate(UriReferenceGenerator.randomLongUri(schema, alphabet, context.random()));
            case RANDOM -> result(randomWithRetry());
        };
    }

    @Override
    protected String generateCandidate() {
        var random = context.random();
        var alphabet = RandomUtil.randomOne(alphabets, random);
        return switch (random.nextInt(10)) {
            case 0 -> "telnet://" + Ipv4Generator.randomIpv4(random) + "/";
            case 1 -> "mailto:" + EmailGenerator.randomEmail(alphabet, random);
            default -> UriReferenceGenerator.randomAbsoluteUri(schema, alphabet, random);
        };
    }
}
