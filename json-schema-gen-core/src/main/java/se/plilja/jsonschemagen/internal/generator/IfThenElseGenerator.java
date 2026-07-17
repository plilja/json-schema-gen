package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.ArrayList;
import java.util.List;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.Schema;

/**
 * Generator for schemas carrying {@code if}/{@code then}/{@code else}.
 *
 * <p>Each generated value honours exactly one conditional branch: either a
 * value that satisfies {@code if} and {@code then}, or a value that fails
 * {@code if} and satisfies {@code else}. Over repeated calls both branches
 * are exercised when both are satisfiable.
 */
final class IfThenElseGenerator extends PhaseGenerator<IfThenElseGenerator.GenerationPhase, Object> {

    private final SchemaValidator validator;
    private final Schema validationTarget;
    private final Schema ifAndThenAndParent;
    private final Schema elseAndParent;

    enum GenerationPhase {
        THEN, ELSE, RANDOM
    }

    IfThenElseGenerator(GeneratorContext context, Schema parent) {
        super(GenerationPhase.class, context);
        this.validator = new SchemaValidator(context);
        this.validationTarget = parent;

        // Strip the conditional keywords so the branch schemas composed below
        // (and anything generated from base) don't re-dispatch back into this
        // generator; if/then/else is applied once, here.
        var base = parent.toBuilder()
                .ifSchema(null)
                .thenSchema(null)
                .elseSchema(null)
                .additionalConditionals(null)
                .build();
        var conditionals = parent.getConditionals();

        this.ifAndThenAndParent = composeIfAndThen(base, conditionals);
        this.elseAndParent = composeElse(base, conditionals);
    }

    /**
     * Composes the branch honoured when every {@code if} matches: the base
     * schema merged with each conditional's {@code if} and (when present)
     * {@code then}. Returns {@code null} when the merge is unsatisfiable, in
     * which case the then branch is skipped in favour of else.
     */
    private static Schema composeIfAndThen(Schema base, List<Schema.Conditional> conditionals) {
        var branches = new ArrayList<Schema>();
        branches.add(base);
        for (var conditional : conditionals) {
            branches.add(conditional.ifSchema());
            if (conditional.thenSchema() != null) {
                branches.add(conditional.thenSchema());
            }
        }
        try {
            return SchemaMerger.merge(branches);
        } catch (UnsatisfiableSchemaException unsatisfiable) {
            // Branch incompatible with the parent -- drop it (null) so the
            // other branch can still generate. See generatePhase.
            return null;
        }
    }

    /**
     * Composes the branch honoured when {@code if} fails: the base schema
     * merged with each conditional's {@code else}, or the base alone when no
     * conditional declares one. The additional requirement that the value
     * fail {@code if} is not encoded here — it is enforced by validating
     * each candidate.
     */
    private static Schema composeElse(Schema base, List<Schema.Conditional> conditionals) {
        var branches = new ArrayList<Schema>();
        branches.add(base);
        for (var conditional : conditionals) {
            if (conditional.elseSchema() != null) {
                branches.add(conditional.elseSchema());
            }
        }
        if (branches.size() == 1) {
            return base;
        }
        try {
            return SchemaMerger.merge(branches);
        } catch (UnsatisfiableSchemaException unsatisfiable) {
            // Branch incompatible with the parent -- drop it (null) so the
            // other branch can still generate. See generatePhase.
            return null;
        }
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return ifAndThenAndParent != null ? GenerationPhase.THEN : GenerationPhase.ELSE;
    }

    /**
     * The deliberate value set is the satisfiable branches — {@code then} and
     * {@code else}. An unsatisfiable branch is excluded so full coverage stays
     * reachable; full coverage means each satisfiable branch has been emitted.
     */
    @Override
    public long totalCount() {
        long thenBranch = ifAndThenAndParent != null ? 1 : 0;
        long elseBranch = elseAndParent != null ? 1 : 0;
        return thenBranch + elseBranch;
    }

    @Override
    public long emittedCount() {
        long emitted = 0;
        if (ifAndThenAndParent != null && GenerationPhase.THEN.ordinal() < currentPhaseOrdinal()) {
            emitted++;
        }
        if (elseAndParent != null && GenerationPhase.ELSE.ordinal() < currentPhaseOrdinal()) {
            emitted++;
        }
        return emitted;
    }

    @Override
    protected GenerationResult<Object> generatePhase(GenerationPhase phase) {
        var composed = switch (phase) {
            case THEN -> ifAndThenAndParent;
            case ELSE -> elseAndParent;
            case RANDOM -> randomBranch();
        };
        if (composed == null) {
            return GenerationResult.skip();
        }
        var candidate = context.generatorFor(composed).generate();
        if (validator.satisfies(candidate, validationTarget)) {
            return result(candidate);
        }
        return GenerationResult.skip();
    }

    private Schema randomBranch() {
        if (ifAndThenAndParent == null) {
            return elseAndParent;
        }
        if (elseAndParent == null) {
            return ifAndThenAndParent;
        }
        return context.random().nextBoolean() ? ifAndThenAndParent : elseAndParent;
    }
}
