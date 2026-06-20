package se.plilja.jsonschemagen.internal.generator.format;

import se.plilja.jsonschemagen.internal.generator.Generator;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.model.StringSchema;

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
