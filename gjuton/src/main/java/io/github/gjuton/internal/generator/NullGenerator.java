package io.github.gjuton.internal.generator;

final class NullGenerator implements Generator<Object> {

    private final GeneratorContext context;

    NullGenerator(GeneratorContext context) {
        this.context = context;
    }

    @Override
    public Object generate() {
        context.registerVisit(this, 0);
        return null;
    }
}
