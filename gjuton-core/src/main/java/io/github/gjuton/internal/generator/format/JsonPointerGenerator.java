package io.github.gjuton.internal.generator.format;

import static io.github.gjuton.internal.generator.GenerationResult.result;

import io.github.gjuton.internal.generator.GenerationResult;
import io.github.gjuton.internal.generator.GeneratorContext;
import io.github.gjuton.internal.model.StringSchema;
import io.github.gjuton.internal.util.RandomUtil;
import java.util.Random;

/**
 * Generates RFC 6901 JSON Pointer strings.
 *
 * <p>A JSON Pointer is either the empty string (referencing the root of the document)
 * or a sequence of {@code /}-prefixed reference tokens (e.g. {@code /foo/0/bar}).
 * Each token names a property or array index within the target document.
 */
public final class JsonPointerGenerator extends StringFormatGenerator<JsonPointerGenerator.JsonPointerPhase> {

    enum JsonPointerPhase {
        EMPTY, RANDOM
    }

    public JsonPointerGenerator(GeneratorContext context, StringSchema schema) {
        super(JsonPointerPhase.class, context, schema);
    }

    @Override
    protected JsonPointerPhase minimalPhase() {
        return JsonPointerPhase.RANDOM;
    }

    @Override
    protected GenerationResult<String> generatePhase(JsonPointerPhase phase) {
        return switch (phase) {
            case EMPTY -> tryCandidate("");
            case RANDOM -> result(randomWithRetry());
        };
    }

    @Override
    protected String generateCandidate() {
        int segments = context.random().nextInt(1, 5);
        return randomPointer(context.random(), segments);
    }

    /**
     * Builds a random JSON Pointer with the given number of {@code /}-prefixed segments.
     * Each segment is either a small integer (array index), a random lowercase word,
     * or a word containing {@code ~0}/{@code ~1} escape sequences (the RFC 6901 encodings
     * of {@code ~} and {@code /}).
     */
    static String randomPointer(Random random, int segments) {
        var sb = new StringBuilder();
        for (int i = 0; i < segments; i++) {
            sb.append('/');
            int kind = random.nextInt(3);
            if (kind == 0) {
                sb.append(random.nextInt(0, 20));
            } else if (kind == 1) {
                var len = random.nextInt(1, 6);
                sb.append(RandomUtil.randomStringOfLength(len, random));
                sb.append("~").append(random.nextInt(2));
            } else {
                var len = random.nextInt(1, 11);
                sb.append(RandomUtil.randomStringOfLength(len, random));
            }
        }
        return sb.toString();
    }
}
