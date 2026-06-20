package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;
import static se.plilja.jsonschemagen.internal.generator.GenerationResult.skip;
import static se.plilja.jsonschemagen.internal.util.FunctionalUtil.coalesce;

import java.util.ArrayList;
import java.util.List;
import se.plilja.jsonschemagen.internal.model.ArraySchema;
import se.plilja.jsonschemagen.internal.model.NullSchema;
import se.plilja.jsonschemagen.internal.model.Schema;

/**
 * Generator for {@code "type": "array"} schemas. Varies array length
 * across successive calls to cover the allowed range.
 */
final class ArrayGenerator extends PhaseGenerator<ArrayGenerator.GenerationPhase, List<Object>> {

    private static final int DEFAULT_LENGTH_BUFFER = 5;

    private final ArraySchema schema;
    private final Schema itemSchema;

    enum GenerationPhase {
        MIN_LENGTH, MAX_LENGTH, RANDOM
    }

    ArrayGenerator(GeneratorContext context, ArraySchema schema) {
        super(GenerationPhase.class, context);
        this.schema = schema;
        // TODO: when items is absent, JSON Schema allows any element type. Emitting nulls is valid
        // but boring; a varied-type generator (cycling string/int/bool/...) would surface more bugs.
        this.itemSchema = coalesce(schema.getItems(), new NullSchema());
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.MIN_LENGTH;
    }

    @Override
    protected GenerationResult<List<Object>> generatePhase(GenerationPhase phase) {
        return switch (phase) {
            case MIN_LENGTH -> result(buildList(coalesce(schema.getMinItems(), 0)));
            case MAX_LENGTH -> schema.getMaxItems() != null ? result(buildList(schema.getMaxItems())) : skip();
            case RANDOM -> {
                var min = coalesce(schema.getMinItems(), 0);
                var max = coalesce(schema.getMaxItems(), min + DEFAULT_LENGTH_BUFFER);
                var length = min + context.random().nextInt(max - min + 1);
                yield result(buildList(length));
            }
        };
    }

    private List<Object> buildList(int length) {
        var list = new ArrayList<>();
        for (var i = 0; i < length; i++) {
            list.add(context.generatorFor(itemSchema).generate());
        }
        return list;
    }
}
