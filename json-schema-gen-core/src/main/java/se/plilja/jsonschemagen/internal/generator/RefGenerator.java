package se.plilja.jsonschemagen.internal.generator;

import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;

/**
 * Generator for schemas with a {@code $ref} keyword. A {@code $ref}
 * replaces the schema it appears in with the referenced schema, allowing
 * schema reuse and recursive definitions.
 */
final class RefGenerator implements Generator<Object> {

    // TODO make these configurable via the public API once a use case appears.
    // SOFT_DEPTH flips the context into minimal mode so recursive generators
    // collapse to required-only / empty containers. HARD_DEPTH is the ceiling
    // for required-field recursion that can never bottom out — beyond it the
    // schema is unsatisfiable. The depth is per-RefGenerator (i.e. per target
    // schema since the generator cache is identity-keyed), so under mutual
    // recursion (A↔B) the effective total nesting ceiling is HARD_DEPTH × cycle
    // length — still bounded, just looser.
    private static final int SOFT_DEPTH = 5;
    private static final int HARD_DEPTH = 10;

    private final GeneratorContext context;
    private final String ref;
    private int depth;

    RefGenerator(GeneratorContext context, String ref) {
        this.context = context;
        this.ref = ref;
    }

    @Override
    public Object generate() {
        if (depth >= HARD_DEPTH) {
            throw new UnsatisfiableSchemaException(
                    "Recursive $ref '" + ref + "' could not bottom out within " + HARD_DEPTH
                            + " levels — schema appears to require infinite recursion");
        }
        var target = context.generatorForRef(ref);
        var enterMinimal = depth + 1 >= SOFT_DEPTH && !context.isMinimal();
        if (enterMinimal) {
            context.enterMinimal();
        }
        depth++;
        try {
            return target.generate();
        } finally {
            depth--;
            if (enterMinimal) {
                context.exitMinimal();
            }
        }
    }
}
