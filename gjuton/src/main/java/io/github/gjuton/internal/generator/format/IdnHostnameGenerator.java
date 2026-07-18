package io.github.gjuton.internal.generator.format;

import io.github.gjuton.internal.generator.Generator;
import io.github.gjuton.internal.generator.GeneratorContext;
import io.github.gjuton.internal.model.StringSchema;

/**
 * Generates internationalised hostname strings for the {@code idn-hostname} format.
 */
public final class IdnHostnameGenerator implements Generator<String> {

    private final HostnameGenerator delegate;
    private boolean emitted;

    public IdnHostnameGenerator(GeneratorContext context, StringSchema schema) {
        this.delegate = new HostnameGenerator(context, schema, Alphabets.IDN_CANONICAL, Alphabets.IDN_POOL);
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
