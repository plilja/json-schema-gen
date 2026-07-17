package io.github.gjuton.internal.generator;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.Schema;

/**
 * Generator for a schema whose only constraint the type-based generators
 * cannot honor is {@code not}: it must yield a value that satisfies the
 * schema as a whole — including any sibling {@code type} or other
 * keywords — while not matching the {@code not} sub-schema.
 *
 * <p>Best-effort. Some satisfiable schemas (e.g. a {@code not} alongside a
 * narrow numeric range) may have no value this generator can find; it then
 * throws {@link UnsatisfiableSchemaException}. A thrown exception therefore
 * means "no value found," not "provably unsatisfiable."
 */
final class NotGenerator implements Generator<Object> {

    private final SchemaValidator validator;
    private final Schema schema;
    private boolean emitted;

    NotGenerator(GeneratorContext context, Schema schema) {
        this.validator = new SchemaValidator(context);
        this.schema = schema;
    }

    @Override
    public Object generate() {
        var value = Probes.firstMatching(candidate -> validator.satisfies(candidate, schema));
        if (value == Probes.NO_MATCH) {
            throw new UnsatisfiableSchemaException("No candidate value satisfies the schema's 'not' constraint");
        }
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
