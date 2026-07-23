package io.github.gjuton.internal.generator.format;

import io.github.gjuton.internal.generator.Generator;
import io.github.gjuton.internal.generator.GeneratorContext;
import io.github.gjuton.internal.model.StringSchema;

/**
 * Generates internationalised absolute URI strings for the {@code iri}
 * format (RFC 3987).
 */
public final class IriGenerator implements Generator<String> {

    private final UriGenerator delegate;

    public IriGenerator(GeneratorContext context, StringSchema schema) {
        this.delegate = new UriGenerator(context, schema, Alphabets.IDN_POOL);
    }

    @Override
    public String generate() {
        return delegate.generate();
    }
}
