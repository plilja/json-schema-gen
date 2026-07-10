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
                .build();

        this.ifAndThenAndParent = composeIfAndThen(base, parent);
        this.elseAndParent = composeElse(base, parent);
    }

    /**
     * Composes the branch honoured when {@code if} matches: the base schema
     * merged with {@code if}, and with {@code then} when it is present.
     * Returns {@code null} when the merge is unsatisfiable, in which case
     * the then branch is skipped in favour of else.
     */
    private static Schema composeIfAndThen(Schema base, Schema parent) {
        var branches = new ArrayList<Schema>();
        branches.add(base);
        branches.add(parent.getIfSchema());
        if (parent.getThenSchema() != null) {
            branches.add(parent.getThenSchema());
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
     * merged with {@code else}, or the base alone when {@code else} is
     * absent. The additional requirement that the value fail {@code if} is
     * not encoded here — it is enforced by validating each candidate.
     */
    private static Schema composeElse(Schema base, Schema parent) {
        if (parent.getElseSchema() == null) {
            return base;
        }
        try {
            return SchemaMerger.merge(List.of(base, parent.getElseSchema()));
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
