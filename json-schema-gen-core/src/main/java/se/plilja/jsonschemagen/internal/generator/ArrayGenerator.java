package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.FunctionalUtil.coalesce;
import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;
import static se.plilja.jsonschemagen.internal.generator.GenerationResult.skip;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import se.plilja.jsonschemagen.internal.model.ArraySchema;
import se.plilja.jsonschemagen.internal.model.NullSchema;

final class ArrayGenerator extends PhaseGenerator<ArrayGenerator.GenerationPhase, List<Object>> {

    private static final int DEFAULT_LENGTH_BUFFER = 5;

    private final Random random;
    private final ArraySchema schema;
    private final JsonGenerator itemGenerator;

    enum GenerationPhase {
        MIN_LENGTH, MAX_LENGTH, RANDOM
    }

    ArrayGenerator(Random random, ArraySchema schema) {
        super(GenerationPhase.class);
        this.random = random;
        this.schema = schema;
        // TODO: when items is absent, JSON Schema allows any element type. Emitting nulls is valid
        // but boring; a varied-type generator (cycling string/int/bool/...) would surface more bugs.
        this.itemGenerator = new JsonGenerator(coalesce(schema.getItems(), new NullSchema()), random);
    }

    @Override
    protected GenerationResult<List<Object>> generatePhase(GenerationPhase phase) {
        return switch (phase) {
            case MIN_LENGTH -> schema.getMinItems() != null ? result(buildList(schema.getMinItems())) : skip();
            case MAX_LENGTH -> schema.getMaxItems() != null ? result(buildList(schema.getMaxItems())) : skip();
            case RANDOM -> {
                var min = coalesce(schema.getMinItems(), 0);
                var max = coalesce(schema.getMaxItems(), min + DEFAULT_LENGTH_BUFFER);
                var length = min + random.nextInt(max - min + 1);
                yield result(buildList(length));
            }
        };
    }

    private List<Object> buildList(int length) {
        var list = new ArrayList<>();
        for (var i = 0; i < length; i++) {
            list.add(itemGenerator.generate());
        }
        return list;
    }
}
