package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.GenerationResult.result;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.generator.GenerationResult;
import io.github.gjuton.internal.generator.GeneratorContext;
import io.github.gjuton.internal.model.StringSchema;

/**
 * Generates uncompressed colon-separated IPv6 address strings for the
 * {@code ipv6} format (RFC 4291).
 */
public final class Ipv6Generator extends StringFormatGenerator<Ipv6Generator.Ipv6Phase> {

    // Eight groups joined by ':' with leading zeros suppressed (RFC 4291 §2.2 allows this):
    // "0:0:0:0:0:0:0:0" (15) through "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff" (39).
    private static final int MIN_IPV6_LENGTH = 15;
    private static final int MAX_IPV6_LENGTH = 39;
    private static final int GROUPS = 8;

    protected enum Ipv6Phase {
        RANDOM
    }

    public Ipv6Generator(GeneratorContext context, StringSchema schema) {
        super(Ipv6Phase.class, context, schema);
    }

    @Override
    protected Ipv6Phase minimalPhase() {
        return Ipv6Phase.RANDOM;
    }

    @Override
    protected GenerationResult<String> generatePhase(Ipv6Phase phase) {
        if (schema.getMinLength() != null && schema.getMinLength() > MAX_IPV6_LENGTH
                || schema.getMaxLength() != null && schema.getMaxLength() < MIN_IPV6_LENGTH) {
            throw new UnsatisfiableSchemaException(
                    "Uncompressed IPv6 addresses are between " + MIN_IPV6_LENGTH + " and " + MAX_IPV6_LENGTH
                            + " characters; schema length bounds exclude that");
        }
        return result(randomWithRetry());
    }

    @Override
    protected String generateCandidate() {
        var sb = new StringBuilder(MAX_IPV6_LENGTH);
        for (int i = 0; i < GROUPS; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(Integer.toHexString(context.random().nextInt(0x10000)));
        }
        return sb.toString();
    }
}
