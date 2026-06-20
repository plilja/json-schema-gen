package se.plilja.jsonschemagen.internal.generator;

import java.util.ArrayList;
import se.plilja.jsonschemagen.internal.model.Schema;

/**
 * Generator for schemas with an {@code allOf} keyword. Generates values
 * satisfying all branches simultaneously.
 */
final class AllOfGenerator implements Generator<Object> {

    private final GeneratorContext context;
    private final Schema merged;

    AllOfGenerator(GeneratorContext context, Schema parent) {
        this.context = context;
        if (parent.getAllOf().isEmpty()) {
            throw new IllegalArgumentException("allOf must contain at least one sub-schema");
        }
        var branches = new ArrayList<>(parent.getAllOf());
        branches.add(parent.toBuilder().allOf(null).build());
        this.merged = SchemaMerger.merge(branches);
    }

    @Override
    public Object generate() {
        return context.generatorFor(merged).generate();
    }
}
