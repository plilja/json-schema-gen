package se.plilja.jsonschemagen.internal.generator;

import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.Schema;

/**
 * Generator for schemas with a {@code const} keyword. Always emits the
 * exact value declared in the schema, regardless of how many times
 * {@link #generate()} is called.
 *
 * <p>When combining keywords ({@code oneOf}, {@code anyOf}, {@code allOf},
 * {@code if}/{@code then}/{@code else}) accompany the {@code const}, the
 * declared value must satisfy them; otherwise the schema is unsatisfiable
 * and construction fails with {@link UnsatisfiableSchemaException}.
 */
final class ConstGenerator implements Generator<Object> {

    private final Object value;
    private boolean emitted;

    ConstGenerator(GeneratorContext context, Object value, Schema validationTarget) {
        // A const has a single candidate, so if it violates a sibling
        // combining keyword there is nothing to retry — the schema is
        // unsatisfiable.
        var validator = new SchemaValidator(context);
        if (!validator.satisfies(value, validationTarget)) {
            throw new UnsatisfiableSchemaException("The const value does not satisfy the schema");
        }
        this.value = value;
    }

    @Override
    public Object generate() {
        emitted = true;
        return value;
    }

    @Override
    public long emittedCount() {
        return emitted ? 1 : 0;
    }

    @Override
    public long totalCount() {
        return 1;
    }
}
