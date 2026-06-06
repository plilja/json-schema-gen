package se.plilja.jsonschemagen.internal.generator;

import java.util.Random;
import se.plilja.jsonschemagen.internal.model.BooleanSchema;
import se.plilja.jsonschemagen.internal.model.IntegerSchema;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.model.StringSchema;

public final class JsonGenerator {

    private final PhaseGenerator<?, ?> delegate;

    public JsonGenerator(Long seed, Schema schema) {
        Random random = seed != null ? new Random(seed) : new Random();
        this.delegate = switch (schema) {
            case StringSchema s -> new StringGenerator(random, s);
            case IntegerSchema s -> new LongGenerator(random, s);
            case BooleanSchema ignored -> new BooleanGenerator(random);
        };
    }

    public Object generate() {
        return delegate.generate();
    }
}
