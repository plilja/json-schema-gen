package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;
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

    /**
     * Values likely to violate a typical property constraint, tried in
     * order when disambiguating an over-matching {@code oneOf} candidate.
     * Deliberately type-diverse: a probe only helps if it fails the target
     * property's own schema.
     */
    private static final List<Object> DISAMBIGUATION_PROBES =
            List.of(Boolean.TRUE, BigDecimal.ONE, "disambiguation-probe", List.of(), Map.of());

    private final Schema parent;
    private final Schema validationTarget;
    private final SchemaValidator validator;
    private final Schema base;
    private final List<Schema> oneOfSubSchemas;
    private final List<Schema> anyOfSubSchemas;
    private int index = 0;

    enum GenerationPhase {
        EXHAUSTIVE, RANDOM
    }

    AnyOfAllOfOneOfGenerator(GeneratorContext context, Schema parent) {
        super(GenerationPhase.class, context);
        this.parent = parent;
        // allOf is excluded: SchemaMerger treats additionalProperties:false
        // as applying to the union of all branches' properties, so per-branch
        // validation would reject values that are correct under our merge.
        this.validationTarget = parent.toBuilder().allOf(null).build();
        this.validator = new SchemaValidator(context);
        var baseTemp = parent.toBuilder()
                .oneOf(null)
                .anyOf(null)
                .allOf(null)
                .build();

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
            branches.add(baseTemp);
            this.base = SchemaMerger.merge(branches);
        } else {
            this.base = baseTemp;
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

    /**
     * Generates a candidate from the picked (approximated) schema, then
     * checks it against the original, unmerged {@code oneOf}/{@code anyOf}
     * clauses — catching a branch that turns out to also match another
     * (or none at all). {@code allOf} is intentionally excluded; see
     * {@link #validationTarget}. A candidate that over-matches a
     * {@code oneOf} clause is repaired by {@link #disambiguateOneOf};
     * anything else that fails validation is skipped so
     * {@link PhaseGenerator} retries.
     */
    @Override
    protected GenerationResult<Object> generatePhase(GenerationPhase phase) {
        var schema = switch (phase) {
            case EXHAUSTIVE -> pickExhaustive();
            case RANDOM -> pickRandom();
        };
        var candidate = context.generatorFor(schema).generate();
        if (validator.satisfies(candidate, validationTarget)) {
            return result(candidate);
        }
        var disambiguated = disambiguateOneOf(candidate);
        if (disambiguated != null) {
            return result(disambiguated);
        }
        return GenerationResult.skip();
    }

    /**
     * Repairs a candidate that satisfies more than one {@code oneOf} branch
     * by mutating a property that an extra matching branch constrains —
     * but the candidate doesn't yet violate — to a value that breaks it,
     * so only the intended branch still matches. Returns {@code null} if
     * the candidate isn't an object, doesn't over-match, or no such
     * property/value combination exists.
     */
    @SuppressWarnings("unchecked")
    private Object disambiguateOneOf(Object candidate) {
        if (!(candidate instanceof Map<?, ?> rawMap) || parent.getOneOf() == null) {
            return null;
        }
        var map = (Map<String, Object>) rawMap;
        var matching = parent.getOneOf().stream()
                .filter(branch -> validator.satisfies(candidate, branch))
                .toList();
        if (matching.size() <= 1) {
            return null;
        }
        for (var extraMatch : matching.subList(1, matching.size())) {
            var mutated = violateBranch(map, extraMatch);
            if (mutated != null && validator.satisfies(mutated, validationTarget)) {
                return mutated;
            }
        }
        return null;
    }

    /**
     * Looks for a property that {@code branch} constrains, and a probe
     * value that violates that constraint, then returns a copy of
     * {@code map} with that property set to the probe. Returns
     * {@code null} if no property/probe combination violates the branch.
     */
    private Object violateBranch(Map<String, Object> map, Schema branch) {
        if (!(branch instanceof ObjectSchema objectBranch)) {
            return null;
        }
        for (var entry : objectBranch.getProperties().entrySet()) {
            for (var probe : DISAMBIGUATION_PROBES) {
                if (!validator.satisfies(probe, entry.getValue())) {
                    var mutated = new LinkedHashMap<>(map);
                    mutated.put(entry.getKey(), probe);
                    return mutated;
                }
            }
        }
        return null;
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
