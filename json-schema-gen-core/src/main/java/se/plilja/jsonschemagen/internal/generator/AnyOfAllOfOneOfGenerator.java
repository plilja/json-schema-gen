package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.ArrayList;
import java.util.List;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.util.RandomUtil;

/**
 * Generator for schemas carrying one or more combining keywords
 * ({@code allOf}, {@code oneOf}, {@code anyOf}).
 *
 * <p>Resolves {@code allOf} first by merging all branches with the core
 * schema into a single base. Then handles {@code oneOf} and {@code anyOf}
 * by cycling through branches exhaustively before switching to random
 * selection.
 */
final class AnyOfAllOfOneOfGenerator extends PhaseGenerator<AnyOfAllOfOneOfGenerator.GenerationPhase, Object> {

    private final Schema base;
    private final List<Schema> oneOfSubSchemas;
    private final List<Schema> anyOfSubSchemas;
    private int index = 0;

    enum GenerationPhase {
        EXHAUSTIVE, RANDOM
    }

    AnyOfAllOfOneOfGenerator(GeneratorContext context, Schema parent) {
        super(GenerationPhase.class, context);
        var core = parent.toBuilder().oneOf(null).anyOf(null).allOf(null).build();

        if (parent.getAllOf() != null) {
            if (parent.getAllOf().isEmpty()) {
                throw new IllegalArgumentException("allOf must contain at least one sub-schema");
            }
            var branches = new ArrayList<Schema>();
            for (var branch : parent.getAllOf()) {
                if (branch.getRef() != null) {
                    var resolved = context.resolveRef(branch.getRef());
                    // Identity check: the parser reuses the same Schema instance for "#",
                    // so == detects self-referential $ref. Self-ref is tautological in
                    // allOf — the value already satisfies this schema by construction.
                    if (resolved == parent) {
                        continue;
                    }
                    branches.add(resolved);
                } else {
                    branches.add(branch);
                }
            }
            branches.add(core);
            this.base = SchemaMerger.merge(branches);
        } else {
            this.base = core;
        }

        if (parent.getOneOf() != null) {
            this.oneOfSubSchemas = SchemaMerger.mergeEachWith(parent.getOneOf(), base);
            if (oneOfSubSchemas.isEmpty()) {
                throw new UnsatisfiableSchemaException(
                        "oneOf has no branch compatible with the parent schema");
            }
        } else {
            this.oneOfSubSchemas = List.of();
        }

        if (parent.getAnyOf() != null) {
            this.anyOfSubSchemas = SchemaMerger.mergeEachWith(parent.getAnyOf(), base);
            if (anyOfSubSchemas.isEmpty()) {
                throw new UnsatisfiableSchemaException(
                        "anyOf has no branch compatible with the parent schema");
            }
        } else {
            this.anyOfSubSchemas = List.of();
        }
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.EXHAUSTIVE;
    }

    @Override
    protected GenerationPhase advanceToNext(GenerationPhase current) {
        if (current == GenerationPhase.EXHAUSTIVE) {
            index++;
            int maxSize = Math.max(oneOfSubSchemas.size(), anyOfSubSchemas.size());
            if (maxSize > 0 && index < maxSize) {
                return GenerationPhase.EXHAUSTIVE;
            }
        }
        return super.advanceToNext(current);
    }

    @Override
    protected GenerationResult<Object> generatePhase(GenerationPhase phase) {
        var schema = switch (phase) {
            case EXHAUSTIVE -> pickExhaustive();
            case RANDOM -> pickRandom();
        };
        return result(context.generatorFor(schema).generate());
    }

    /**
     * Selects the next branch from each sub-schema list using the current
     * index, wrapping around shorter lists. Falls back to the base schema
     * when no oneOf or anyOf branches exist (allOf-only case).
     */
    private Schema pickExhaustive() {
        if (oneOfSubSchemas.isEmpty() && anyOfSubSchemas.isEmpty()) {
            return base;
        }
        if (anyOfSubSchemas.isEmpty()) {
            return oneOfSubSchemas.get(index % oneOfSubSchemas.size());
        }
        if (oneOfSubSchemas.isEmpty()) {
            return anyOfSubSchemas.get(index % anyOfSubSchemas.size());
        }
        return SchemaMerger.merge(List.of(
                oneOfSubSchemas.get(index % oneOfSubSchemas.size()),
                anyOfSubSchemas.get(index % anyOfSubSchemas.size())));
    }

    /**
     * Selects a random oneOf branch and a random anyOf subset, merging them.
     * Falls back to the base schema when no oneOf or anyOf branches exist.
     */
    private Schema pickRandom() {
        if (oneOfSubSchemas.isEmpty() && anyOfSubSchemas.isEmpty()) {
            return base;
        }
        if (anyOfSubSchemas.isEmpty()) {
            return RandomUtil.randomOne(oneOfSubSchemas, context.random());
        }
        if (oneOfSubSchemas.isEmpty()) {
            return mergeRandomAnyOfSubset();
        }
        var oneOfPick = RandomUtil.randomOne(oneOfSubSchemas, context.random());
        var anyOfPick = mergeRandomAnyOfSubset();
        return SchemaMerger.merge(List.of(oneOfPick, anyOfPick));
    }

    /**
     * Picks a random subset of anyOf branches and merges them. Starts with
     * a random size N and falls back to smaller subsets if the merge is
     * unsatisfiable. N=1 always succeeds because each branch was proven
     * compatible with the base during construction.
     */
    private Schema mergeRandomAnyOfSubset() {
        for (int n = context.random().nextInt(anyOfSubSchemas.size()) + 1; n >= 1; n--) {
            var subset = RandomUtil.randomSubset(anyOfSubSchemas, n, context.random());
            try {
                return SchemaMerger.merge(subset);
            } catch (UnsatisfiableSchemaException ignored) {
                // try smaller
            }
        }
        throw new IllegalStateException("Unreachable: N=1 must succeed");
    }
}
