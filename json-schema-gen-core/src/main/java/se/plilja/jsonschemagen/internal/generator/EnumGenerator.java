package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.List;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.Schema;

/**
 * Generator for schemas with an {@code enum} keyword. An {@code enum}
 * restricts the value to a fixed set of allowed literals.
 *
 * <p>When combining keywords ({@code oneOf}, {@code anyOf}, {@code allOf},
 * {@code if}/{@code then}/{@code else}) accompany the {@code enum}, only
 * literals that also satisfy those keywords are produced. If no literal
 * satisfies the full schema, it is unsatisfiable.
 */
final class EnumGenerator extends PhaseGenerator<EnumGenerator.GenerationPhase, Object> {

    private final List<Object> values;
    private int index = 0;

    enum GenerationPhase {
        EXHAUSTIVE, RANDOM
    }

    EnumGenerator(GeneratorContext context, List<Object> values, Schema validationTarget) {
        super(GenerationPhase.class, context);
        // Combining keywords further restrict the enum. Filter the finite
        // candidate set up front so no phase can emit an invalid literal;
        // generate-then-retry could spuriously exhaust when the valid
        // literals are a minority.
        var validator = new SchemaValidator(context);
        this.values = values.stream()
                .filter(value -> validator.satisfies(value, validationTarget))
                .toList();
        if (this.values.isEmpty()) {
            throw new UnsatisfiableSchemaException("No enum value satisfies the schema");
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
            if (index < values.size()) {
                return GenerationPhase.EXHAUSTIVE;
            }
        }
        return super.advanceToNext(current);
    }

    @Override
    protected GenerationResult<Object> generatePhase(GenerationPhase phase) {
        var value = switch (phase) {
            case EXHAUSTIVE -> values.get(index);
            case RANDOM -> values.get(context.random().nextInt(values.size()));
        };
        return result(value);
    }
}
