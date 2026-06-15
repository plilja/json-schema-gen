package se.plilja.jsonschemagen.internal.generator.format;

import se.plilja.jsonschemagen.internal.generator.GenerationResult;
import se.plilja.jsonschemagen.internal.generator.GeneratorContext;
import se.plilja.jsonschemagen.internal.generator.PhaseGenerator;
import se.plilja.jsonschemagen.internal.model.StringSchema;

public final class IdnEmailGenerator extends PhaseGenerator<EmailGenerator.EmailPhase, String> {

    private final EmailGenerator delegate;

    public IdnEmailGenerator(GeneratorContext context, StringSchema schema) {
        super(EmailGenerator.EmailPhase.class, context);
        this.delegate = new EmailGenerator(context, schema, Alphabets.IDN_CANONICAL, Alphabets.IDN_POOL);
    }

    @Override
    public String generate() {
        return delegate.generate();
    }

    @Override
    protected EmailGenerator.EmailPhase minimalPhase() {
        return delegate.minimalPhase();
    }

    @Override
    protected GenerationResult<String> generatePhase(EmailGenerator.EmailPhase phase) {
        return delegate.generatePhase(phase);
    }
}
