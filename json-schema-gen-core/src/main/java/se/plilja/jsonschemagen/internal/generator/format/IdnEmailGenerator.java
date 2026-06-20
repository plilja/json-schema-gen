package se.plilja.jsonschemagen.internal.generator.format;

import se.plilja.jsonschemagen.internal.generator.Generator;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.model.StringSchema;

/**
 * Generates internationalised email addresses for the {@code idn-email} format.
 */
public final class IdnEmailGenerator implements Generator<String> {

    private final EmailGenerator delegate;

    public IdnEmailGenerator(GeneratorContext context, StringSchema schema) {
        this.delegate = new EmailGenerator(context, schema, Alphabets.IDN_CANONICAL, Alphabets.IDN_POOL);
    }

    @Override
    public String generate() {
        return delegate.generate();
    }
}
