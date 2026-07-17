package io.github.gjuton.internal.generator;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.Schema;
import java.util.List;

/**
 * Generator for schemas with a {@code $ref} keyword. A {@code $ref}
 * replaces the schema it appears in with the referenced schema, allowing
 * schema reuse and recursive definitions.
 *
 * <p>Depth tracking lives on {@link GeneratorContext} as a global counter
 * across all RefGenerator instances. This prevents exponential blowup on
 * recursive properties.
 */
final class RefGenerator implements Generator<Object> {

    private final GeneratorContext context;
    private final String ref;

    RefGenerator(GeneratorContext context, String ref) {
        this.context = context;
        this.ref = ref;
    }

    @Override
    public Object generate() {
        if (context.getGlobalRefDepth() >= context.refHardDepth()) {
            throw new UnsatisfiableSchemaException(
                    "Recursive $ref '" + ref + "' could not bottom out within " + context.refHardDepth()
                            + " levels — schema appears to require infinite recursion");
        }
        var schema = context.resolveRef(ref);
        var target = context.generatorFor(schema);
        context.incrementGlobalRefDepth();
        try {
            return target.generate();
        } finally {
            context.decrementGlobalRefDepth();
        }
    }

    /**
     * The referenced schema, so its generator is counted once no matter how many
     * {@code $ref} sites point at it. Empty when the ref cannot be resolved —
     * generation surfaces that failure on its own.
     */
    @Override
    public List<Schema> structuralChildren() {
        try {
            return List.of(context.resolveRef(ref));
        } catch (IllegalArgumentException unresolved) {
            return List.of();
        }
    }

    // A $ref has no deliberate values of its own; the resolved target,
    // exposed as a structural child above, carries the count instead.

    @Override
    public long emittedCount() {
        return 0;
    }

    @Override
    public long totalCount() {
        return 0;
    }
}
