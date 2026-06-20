package se.plilja.jsonschemagen.internal.generator.format;

import se.plilja.jsonschemagen.internal.generator.Generator;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.model.StringSchema;

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
