package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;

final class RefGenerator extends PhaseGenerator<RefGenerator.GenerationPhase, Object> {

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

    enum GenerationPhase {
        REF
    }

    private final String ref;
    private int depth;

    RefGenerator(GeneratorContext context, String ref) {
        super(GenerationPhase.class, context);
        this.ref = ref;
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.REF;
    }

    @Override
    protected GenerationResult<Object> generatePhase(GenerationPhase phase) {
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
            return result(target.generate());
        } finally {
            depth--;
            if (enterMinimal) {
                context.exitMinimal();
            }
        }
    }
}
