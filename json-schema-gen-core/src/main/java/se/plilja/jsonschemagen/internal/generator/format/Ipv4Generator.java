package se.plilja.jsonschemagen.internal.generator.format;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.Random;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.generator.GenerationResult;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.model.StringSchema;

/**
 * Generates dotted-quad IPv4 address strings for the {@code ipv4} format.
 */
public final class Ipv4Generator extends StringFormatGenerator<Ipv4Generator.Ipv4Phase> {

    // Dotted-quad: "0.0.0.0" (7) through "255.255.255.255" (15).
    private static final int MIN_IPV4_LENGTH = 7;
    private static final int MAX_IPV4_LENGTH = 15;

    protected enum Ipv4Phase {
        RANDOM
    }

    public Ipv4Generator(GeneratorContext context, StringSchema schema) {
        super(Ipv4Phase.class, context, schema);
    }

    @Override
    protected Ipv4Phase minimalPhase() {
        return Ipv4Phase.RANDOM;
    }

    @Override
    protected GenerationResult<String> generatePhase(Ipv4Phase phase) {
        if (schema.getMinLength() != null && schema.getMinLength() > MAX_IPV4_LENGTH
                || schema.getMaxLength() != null && schema.getMaxLength() < MIN_IPV4_LENGTH) {
            throw new UnsatisfiableSchemaException(
                    "IPv4 dotted-quad addresses are between " + MIN_IPV4_LENGTH + " and " + MAX_IPV4_LENGTH
                            + " characters; schema length bounds exclude that");
        }
        return result(randomWithRetry());
    }

    @Override
    protected String generateCandidate() {
        return randomIpv4(context.random());
    }

    static String randomIpv4(Random random) {
        return random.nextInt(256)
                + "." + random.nextInt(256)
                + "." + random.nextInt(256)
                + "." + random.nextInt(256);
    }
}
