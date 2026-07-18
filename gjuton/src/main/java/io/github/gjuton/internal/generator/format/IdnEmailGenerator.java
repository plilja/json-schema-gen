package io.github.gjuton.internal.generator.format;

import io.github.gjuton.internal.generator.Generator;
import io.github.gjuton.internal.generator.GeneratorContext;
import io.github.gjuton.internal.model.StringSchema;

/**
 * Generates internationalised email addresses for the {@code idn-email} format.
 */
public final class IdnEmailGenerator implements Generator<String> {

    private final EmailGenerator delegate;
    private boolean emitted;

    public IdnEmailGenerator(GeneratorContext context, StringSchema schema) {
        this.delegate = new EmailGenerator(context, schema, Alphabets.IDN_CANONICAL, Alphabets.IDN_POOL);
    }

    @Override
    public String generate() {
        var value = delegate.generate();
        emitted = true;
        return value;
    }

    @Override
    public long emittedCount() {
        return emitted ? 1 : 0;
    }

    @Override
    public long totalCount() {
        return 1;
    }
}
