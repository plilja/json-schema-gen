package se.plilja.jsonschemagen.internal.generator.format;

import se.plilja.jsonschemagen.internal.generator.GenerationResult;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.generator.PhaseGenerator;
import se.plilja.jsonschemagen.internal.model.StringSchema;

public final class IdnHostnameGenerator extends PhaseGenerator<HostnameGenerator.HostnamePhase, String> {

    private final HostnameGenerator delegate;

    public IdnHostnameGenerator(GeneratorContext context, StringSchema schema) {
        super(HostnameGenerator.HostnamePhase.class, context);
        this.delegate = new HostnameGenerator(context, schema, Alphabets.IDN_CANONICAL, Alphabets.IDN_POOL);
    }

    @Override
    public String generate() {
        return delegate.generate();
    }

    @Override
    protected HostnameGenerator.HostnamePhase minimalPhase() {
        return delegate.minimalPhase();
    }

    @Override
    protected GenerationResult<String> generatePhase(HostnameGenerator.HostnamePhase phase) {
        return delegate.generatePhase(phase);
    }
}
