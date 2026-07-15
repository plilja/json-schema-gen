package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;
import static se.plilja.jsonschemagen.internal.util.FunctionalUtil.coalesce;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
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

    /**
     * Retry budget for finding a distinct element when {@code uniqueItems}
     * is set, before giving up as unsatisfiable.
     */
    private static final int UNIQUE_ITEMS_RETRY_BUDGET = 20;

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
        if (effectiveMax < minLength) {
            throw new UnsatisfiableSchemaException(
                    "No valid array length satisfies minItems/maxItems/contains together: effective minimum length "
                            + minLength + " exceeds effective maximum length " + effectiveMax);
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
        if (schema.isUniqueItems()) {
            var seen = new HashSet<>();
            for (var i = 0; i < length; i++) {
                var element = generateDistinctElementAt(i, containsIndex, seen);
                list.add(element);
                seen.add(element);
            }
        } else {
            for (var i = 0; i < length; i++) {
                list.add(generateElementAt(i, containsIndex));
            }
        }
        return list;
    }

    private Object generateElementAt(int index, int containsIndex) {
        var segment = "[" + index + "]";
        return JsonGenerator.generateForPath(context, segment, () -> {
            if (index == containsIndex) {
                return schema.getContains();
            } else if (index < prefixSchemas.size()) {
                return prefixSchemas.get(index);
            } else {
                return itemSchema;
            }
        });
    }

    /**
     * Generates the element at {@code index}, retrying on collision with an
     * already-placed element ({@code seen}) until a distinct value is found.
     *
     * @throws UnsatisfiableSchemaException if no distinct element can be
     *         produced within the retry budget
     */
    private Object generateDistinctElementAt(int index, int containsIndex, Set<Object> seen) {
        for (var attempt = 0; attempt < UNIQUE_ITEMS_RETRY_BUDGET; attempt++) {
            var element = generateElementAt(index, containsIndex);
            if (!seen.contains(element)) {
                return element;
            }
        }
        throw new UnsatisfiableSchemaException(
                "Could not generate a distinct element satisfying uniqueItems within the retry budget");
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
