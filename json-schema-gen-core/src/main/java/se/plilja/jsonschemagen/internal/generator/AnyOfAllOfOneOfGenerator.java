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

    private final Schema validationTarget;
    private final SchemaValidator validator;
    private final Schema base;
    private final List<List<Schema>> oneOfGroups;
    private final List<List<Schema>> anyOfGroups;
    private int index = 0;

    enum GenerationPhase {
        EXHAUSTIVE, RANDOM
    }

    AnyOfAllOfOneOfGenerator(GeneratorContext context, Schema parent) {
        super(GenerationPhase.class, context);
        this.validator = new SchemaValidator(context);

        var merged = mergeParentWithAllOf(context, parent);

        // Create a base schema without oneOf and anyOf, we will pick from fields
        // anyOfGroups and oneOfGroups when building values
        this.base = merged.toBuilder()
                .oneOf(null)
                .anyOf(null)
                .build();

        // Collect all oneOf/anyOf groups: parent's own + those from allOf merge
        var allOneOfGroups = new ArrayList<List<Schema>>();
        if (parent.getOneOf() != null) {
            allOneOfGroups.addAll(parent.getOneOf());
        }
        if (merged.getOneOf() != null) {
            allOneOfGroups.addAll(merged.getOneOf());
        }

        var allAnyOfGroups = new ArrayList<List<Schema>>();
        if (parent.getAnyOf() != null) {
            allAnyOfGroups.addAll(parent.getAnyOf());
        }
        if (merged.getAnyOf() != null) {
            allAnyOfGroups.addAll(merged.getAnyOf());
        }

        // allOf is excluded from validation: SchemaMerger treats
        // additionalProperties:false as applying to the union of all
        // branches' properties, so per-branch validation would reject
        // values that are correct under our merge.
        this.validationTarget = parent.toBuilder()
                .allOf(null)
                .oneOf(allOneOfGroups.isEmpty() ? null : allOneOfGroups)
                .anyOf(allAnyOfGroups.isEmpty() ? null : allAnyOfGroups)
                .build();

        // Merge the base schema into all the oneOf groups
        this.oneOfGroups = new ArrayList<>();
        for (var group : allOneOfGroups) {
            var mergedGroup = SchemaMerger.mergeEachWith(group, base);
            if (mergedGroup.isEmpty()) {
                throw new UnsatisfiableSchemaException(
                        "oneOf has no branch compatible with the parent schema");
            }
            oneOfGroups.add(mergedGroup);
        }

        // Merge the base schema into all the anyOf groups
        this.anyOfGroups = new ArrayList<>();
        for (var group : allAnyOfGroups) {
            var mergedGroup = SchemaMerger.mergeEachWith(group, base);
            if (mergedGroup.isEmpty()) {
                throw new UnsatisfiableSchemaException(
                        "anyOf has no branch compatible with the parent schema");
            }
            anyOfGroups.add(mergedGroup);
        }
    }

    /**
     * Merges the parent schema with its {@code allOf} branches, resolving
     * {@code $ref}s — each resolved branch's own {@code allOf} is fully
     * merged in as well — and skipping self-referential ones. Returns the
     * parent itself (minus combining keywords) when no {@code allOf} is
     * present.
     *
     * @throws UnsatisfiableSchemaException if a branch's {@code allOf} chain
     *         does not bottom out within
     *         {@link GeneratorContext#GLOBAL_REF_HARD_DEPTH} levels
     */
    private static Schema mergeParentWithAllOf(GeneratorContext context, Schema parent) {
        var baseTemp = parent.toBuilder()
                .oneOf(null)
                .anyOf(null)
                .allOf(null)
                .build();

        Schema merged;
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
                    branches.add(resolveAllOfChain(context, resolved));
                } else {
                    branches.add(branch);
                }
            }
            branches.add(baseTemp);
            merged = SchemaMerger.merge(branches);
        } else {
            merged = baseTemp;
        }
        return merged;
    }

    /**
     * Fully resolves {@code schema}'s own {@code allOf} chain, returning a
     * schema with no unresolved {@code allOf} of its own.
     *
     * @throws UnsatisfiableSchemaException if the chain does not bottom out
     *         within {@link GeneratorContext#GLOBAL_REF_HARD_DEPTH} levels
     */
    private static Schema resolveAllOfChain(GeneratorContext context, Schema schema) {
        if (schema.getAllOf() == null) {
            return schema;
        }
        if (context.getGlobalRefDepth() >= GeneratorContext.GLOBAL_REF_HARD_DEPTH) {
            throw new UnsatisfiableSchemaException(
                    "Recursive allOf $ref could not bottom out within " + GeneratorContext.GLOBAL_REF_HARD_DEPTH
                            + " levels — schema appears to require infinite recursion");
        }
        // Without this, a branch's own unresolved allOf carries forward onto the merged
        // schema, and mutually-recursive $refs (A's allOf -> B, B's allOf -> A) reconstruct
        // a new AnyOfAllOfOneOfGenerator each time that merged schema is generated,
        // recursing without bound.
        context.incrementGlobalRefDepth();
        try {
            return mergeParentWithAllOf(context, schema);
        } finally {
            context.decrementGlobalRefDepth();
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
            int maxSize = 0;
            for (var group : oneOfGroups) {
                maxSize = Math.max(maxSize, group.size());
            }
            for (var group : anyOfGroups) {
                maxSize = Math.max(maxSize, group.size());
            }
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
     * in any group by mutating a property that an extra matching branch
     * constrains to a value that breaks it, so only the intended branch
     * still matches per group. Returns {@code null} if the candidate
     * isn't an object, doesn't over-match, or no fix exists.
     */
    @SuppressWarnings("unchecked")
    private Object disambiguateOneOf(Object candidate) {
        if (!(candidate instanceof Map<?, ?> rawMap) || validationTarget.getOneOf() == null) {
            return null;
        }
        var map = (Map<String, Object>) rawMap;
        for (var group : validationTarget.getOneOf()) {
            var matching = group.stream()
                    .filter(branch -> validator.satisfies(candidate, branch))
                    .toList();
            if (matching.size() <= 1) {
                continue;
            }
            for (var extraMatch : matching.subList(1, matching.size())) {
                var mutated = violateBranch(map, extraMatch);
                if (mutated != null && validator.satisfies(mutated, validationTarget)) {
                    return mutated;
                }
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
     * Selects the next branch from each group using the current index,
     * wrapping around shorter groups. Falls back to the base schema
     * when no oneOf or anyOf groups exist (allOf-only case).
     */
    private Schema pickExhaustive() {
        if (oneOfGroups.isEmpty() && anyOfGroups.isEmpty()) {
            return base;
        }
        var picks = new ArrayList<Schema>();
        for (var group : oneOfGroups) {
            picks.add(group.get(index % group.size()));
        }
        for (var group : anyOfGroups) {
            picks.add(group.get(index % group.size()));
        }
        return SchemaMerger.merge(picks);
    }

    /**
     * Selects a random oneOf branch per group and a random anyOf subset
     * per group, merging all picks. Falls back to the base schema when
     * no groups exist.
     */
    private Schema pickRandom() {
        if (oneOfGroups.isEmpty() && anyOfGroups.isEmpty()) {
            return base;
        }
        var picks = new ArrayList<Schema>();
        for (var group : oneOfGroups) {
            picks.add(RandomUtil.randomOne(group, context.random()));
        }
        for (var group : anyOfGroups) {
            picks.add(mergeRandomAnyOfSubset(group));
        }
        if (picks.size() == 1) {
            return picks.getFirst();
        }
        return SchemaMerger.merge(picks);
    }

    /**
     * Picks a random subset of anyOf branches from a single group and
     * merges them. Starts with a random size N and falls back to smaller
     * subsets if the merge is unsatisfiable. N=1 always succeeds because
     * each branch was proven compatible with the base during construction.
     */
    private Schema mergeRandomAnyOfSubset(List<Schema> group) {
        for (int n = context.random().nextInt(group.size()) + 1; n >= 1; n--) {
            var subset = RandomUtil.randomSubset(group, n, context.random());
            try {
                return SchemaMerger.merge(subset);
            } catch (UnsatisfiableSchemaException ignored) {
                // try smaller
            }
        }
        throw new IllegalStateException("Unreachable: N=1 must succeed");
    }
}
