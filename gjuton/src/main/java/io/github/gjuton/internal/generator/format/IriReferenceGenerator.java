package io.github.gjuton.internal.generator.format;

import io.github.gjuton.internal.generator.Generator;
import io.github.gjuton.internal.generator.GeneratorContext;
import io.github.gjuton.internal.model.StringSchema;

/**
 * Generates internationalised URI-reference strings for the
 * {@code iri-reference} format (RFC 3987).
 */
public final class IriReferenceGenerator implements Generator<String> {

    private final UriReferenceGenerator delegate;

    public IriReferenceGenerator(GeneratorContext context, StringSchema schema) {
        this.delegate = new UriReferenceGenerator(context, schema, Alphabets.IDN_POOL);
    }

    @Override
    public String generate() {
        return delegate.generate();
    }
}
