package se.plilja.jsonschemagen.internal.generator;

final class NullGenerator implements Generator<Object> {

    private boolean emitted;

    @Override
    public Object generate() {
        emitted = true;
        return null;
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
