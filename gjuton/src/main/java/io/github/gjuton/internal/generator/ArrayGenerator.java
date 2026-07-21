package io.github.gjuton.internal.generator;

import static io.github.gjuton.internal.generator.GenerationResult.result;
import static io.github.gjuton.internal.util.FunctionalUtil.coalesce;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.ArraySchema;
import io.github.gjuton.internal.model.NullSchema;
import io.github.gjuton.internal.model.Schema;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * The element schemas the array can actually place, using the same schema
     * instances as generation: reachable tuple-prefix positions, the item schema
     * when there is room past the prefix, and the {@code contains} schema. Bounded
     * by the effective maximum length, so an element schema that a length bound —
     * from the schema or a caller constraint — leaves no room for does not inflate
     * the coverage denominator.
     */
    @Override
    public List<Schema> structuralChildren() {
        var children = new ArrayList<Schema>();
        int maxItems = effectiveMaxLength();
        for (int i = 0; i < prefixSchemas.size() && i < maxItems; i++) {
            children.add(prefixSchemas.get(i));
        }
        if (additionalItemsAllowed && maxItems > prefixSchemas.size()) {
            children.add(itemSchema);
        }
        if (schema.getContains() != null && maxItems > 0) {
            children.add(schema.getContains());
        }
        return children;
    }

    @Override
    protected GenerationResult<List<Object>> generatePhase(GenerationPhase phase) {
        int minLength = effectiveMinLength();
        int effectiveMax = effectiveMaxLength();
        if (effectiveMax < minLength) {
            throw new UnsatisfiableSchemaException(
                    "No valid array length satisfies minItems/maxItems/contains together: effective minimum length "
                            + minLength + " exceeds effective maximum length " + effectiveMax);
        }
        int length = switch (phase) {
            case MIN_LENGTH -> minLength;
            case MAX_LENGTH -> effectiveMax;
            case RANDOM -> minLength + context.random().nextInt(effectiveMax - minLength + 1);
        };
        var value = buildList(length);
        return result(value);
    }

    /**
     * The smallest array length that satisfies the schema and caller constraints
     * together: {@code minItems} raised to the caller's minimum and to at least one
     * when a {@code contains} schema demands an element.
     */
    private int effectiveMinLength() {
        int minLength = coalesce(schema.getMinItems(), 0);
        minLength = Math.max(minLength, context.constraints().arrayMinLength());
        if (schema.getContains() != null) {
            minLength = Math.max(minLength, 1);
        }
        return minLength;
    }

    /**
     * The largest array length this generator produces: the tighter of the schema's
     * {@code maxItems} and the caller's maximum, or a fixed span past the minimum
     * when neither bounds it above, capped at the tuple prefix when
     * {@code additionalItems} is false.
     */
    private int effectiveMaxLength() {
        Integer schemaMax = schema.getMaxItems();
        int constraintMax = context.constraints().arrayMaxLength();
        int upperBound = schemaMax != null ? Math.min(schemaMax, constraintMax) : constraintMax;
        int maxLength = upperBound == Integer.MAX_VALUE
                ? effectiveMinLength() + DEFAULT_LENGTH_BUFFER
                : upperBound;
        if (!additionalItemsAllowed) {
            maxLength = Math.min(maxLength, prefixSchemas.size());
        }
        return maxLength;
    }

    private List<Object> buildList(int length) {
        int containsIndex = pickContainsIndex(length);
        var list = new ArrayList<>();
        if (schema.isUniqueItems()) {
            var seen = new HashSet<>();
            for (int i = 0; i < length; i++) {
                var element = generateDistinctElementAt(i, containsIndex, seen);
                list.add(element);
                seen.add(element);
            }
        } else {
            for (int i = 0; i < length; i++) {
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
        for (int attempt = 0; attempt < UNIQUE_ITEMS_RETRY_BUDGET; attempt++) {
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
