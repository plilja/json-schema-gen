package io.github.gjuton.internal.generator;

import io.github.gjuton.internal.model.NullSchema;
import io.github.gjuton.internal.model.SchemaDocument;
import java.util.Map;
import java.util.Random;

public final class TestContexts {

    private TestContexts() {
    }

    public static GeneratorContext withSeed(long seed) {
        // TODO come up with a better strategy for reading schemas and contexts in unit tests
        return new GeneratorContext(new SchemaDocument(new NullSchema(), Map.of()), new Random(seed));
    }
}
