package se.plilja.jsonschemagen.internal.generator.format;

import se.plilja.jsonschemagen.internal.generator.Generator;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.model.StringSchema;

/**
 * Generates internationalised hostname strings for the {@code idn-hostname} format.
 */
public final class IdnHostnameGenerator implements Generator<String> {

    private final HostnameGenerator delegate;

    public IdnHostnameGenerator(GeneratorContext context, StringSchema schema) {
        this.delegate = new HostnameGenerator(context, schema, Alphabets.IDN_CANONICAL, Alphabets.IDN_POOL);
    }

    @Override
    public String generate() {
        return delegate.generate();
    }
}
