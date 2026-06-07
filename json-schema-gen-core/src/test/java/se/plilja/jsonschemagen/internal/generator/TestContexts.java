package se.plilja.jsonschemagen.internal.generator;

import java.util.Map;
import java.util.Random;
import se.plilja.jsonschemagen.internal.model.NullSchema;
import se.plilja.jsonschemagen.internal.model.SchemaDocument;

final class TestContexts {

    private TestContexts() {
    }

    static GeneratorContext withSeed(long seed) {
        // TODO come up with a better strategy for reading schemas and contexts in unit tests
        return new GeneratorContext(new SchemaDocument(new NullSchema(), Map.of()), new Random(seed));
    }
}
