package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;
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

    /**
     * Extra length past {@code minItems} when the schema has no
     * {@code maxItems}. Gives the generator headroom to produce
     * arrays of varying lengths across phases.
     */
    private static final int DEFAULT_LENGTH_BUFFER = 5;

    private final ArraySchema schema;
    private final List<Schema> prefixSchemas;
    private final Schema itemSchema;
    private final boolean additionalItemsAllowed;

    enum GenerationPhase {
        MIN_LENGTH, MAX_LENGTH, RANDOM
    }

    ArrayGenerator(GeneratorContext context, ArraySchema schema) {
        super(GenerationPhase.class, context);
        this.schema = schema;
        this.prefixSchemas = schema.getPrefixSchemas();
        // TODO: when items is absent, JSON Schema allows any element type. Emitting nulls is valid
        // but boring; a varied-type generator (cycling string/int/bool/...) would surface more bugs.
        this.itemSchema = coalesce(schema.getItemSchema(), new NullSchema());
        this.additionalItemsAllowed = schema.areAdditionalItemsAllowed();
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.MIN_LENGTH;
    }

    @Override
    protected GenerationResult<List<Object>> generatePhase(GenerationPhase phase) {
        int minLength = coalesce(schema.getMinItems(), 0);
        if (schema.getContains() != null) {
            // We need at least 1 item to satisfy the contains even if minItems was smaller
            minLength = Math.max(minLength, 1);
        }
        int effectiveMax = coalesce(schema.getMaxItems(), minLength + DEFAULT_LENGTH_BUFFER);
        if (!additionalItemsAllowed) {
            // additionalItems: false — nothing is allowed past the tuple prefix
            effectiveMax = Math.min(effectiveMax, prefixSchemas.size());
        }
        return switch (phase) {
            case MIN_LENGTH -> result(buildList(minLength));
            case MAX_LENGTH -> result(buildList(effectiveMax));
            case RANDOM -> {
                var length = minLength + context.random().nextInt(effectiveMax - minLength + 1);
                yield result(buildList(length));
            }
        };
    }

    private List<Object> buildList(int length) {
        int containsIndex = pickContainsIndex(length);
        var list = new ArrayList<>();
        for (var i = 0; i < length; i++) {
            if (i == containsIndex) {
                list.add(context.generatorFor(schema.getContains()).generate());
            } else if (i < prefixSchemas.size()) {
                list.add(context.generatorFor(prefixSchemas.get(i)).generate());
            } else {
                list.add(context.generatorFor(itemSchema).generate());
            }
        }
        return list;
    }

    /**
     * Picks an index for the {@code contains} element, avoiding positions
     * inside the tuple prefix when there is room past it.
     */
    private int pickContainsIndex(int length) {
        if (schema.getContains() == null) {
            return -1;
        }
        if (length > prefixSchemas.size()) {
            return prefixSchemas.size() + context.random().nextInt(length - prefixSchemas.size());
        }
        return context.random().nextInt(length);
    }
}
