package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.util.GraphUtil;

/**
 * Generator for {@code "type": "object"} schemas. Varies which
 * optional fields are included across successive calls.
 */
final class ObjectGenerator extends PhaseGenerator<ObjectGenerator.GenerationPhase, Map<String, Object>> {

    private final ObjectSchema schema;

    enum GenerationPhase {
        REQUIRED_ONLY, ALL_FIELDS, RANDOM
    }

    ObjectGenerator(GeneratorContext context, ObjectSchema schema) {
        super(GenerationPhase.class, context);
        this.schema = schema;
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.REQUIRED_ONLY;
    }

    @Override
    protected GenerationResult<Map<String, Object>> generatePhase(GenerationPhase phase) {
        var order = topologicalOrderDependentProperties();
        var selectedProperties = new LinkedHashSet<String>();
        var depRequired = schema.getDependentRequired();

        // Iterate over properties and select which ones to include
        for (var property : order) {
            boolean include;
            if (selectedProperties.contains(property)) {
                // Already included in a previous pass by dependentRequired from an earlier property
                include = true;
            } else if (schema.getRequired().contains(property)) {
                include = true;
            } else {
                // Optional property
                include = switch (phase) {
                    case REQUIRED_ONLY -> false;
                    case ALL_FIELDS -> true;
                    case RANDOM -> context.random().nextBoolean();
                };
            }
            if (include) {
                selectedProperties.add(property);
                selectedProperties.addAll(depRequired.getOrDefault(property, List.of()));
            }
        }

        // If we are below min properties, then pad
        padToMinProperties(selectedProperties, order);

        // Calculate effective schema based on the selected properties and dependentSchemas
        var effectiveSchema = resolveEffectiveSchema(selectedProperties);

        // Generate the return object
        var obj = new LinkedHashMap<String, Object>();
        for (var property : selectedProperties) {
            var fieldSchema = effectiveSchema.getProperties().get(property);
            if (fieldSchema != null) {
                obj.put(property, context.generatorFor(fieldSchema).generate());
            }
        }
        return result(obj);
    }

    private List<String> topologicalOrderDependentProperties() {
        var depRequired = schema.getDependentRequired();
        var properties = new ArrayList<>(schema.getProperties().keySet());
        return GraphUtil.topologicalSort(properties, depRequired);
    }

    private void padToMinProperties(Set<String> selectedProperties, List<String> order) {
        if (schema.getMinProperties() == null) {
            return;
        }
        // Reverse order: dependents are added before their triggers, satisfying dependentRequired
        for (int i = order.size() - 1; i >= 0; i--) {
            if (selectedProperties.size() >= schema.getMinProperties()) {
                break;
            }
            selectedProperties.add(order.get(i));
        }
    }

    /**
     * Merges any dependent schemas triggered by the selected keys
     * with the parent schema. If the merge introduces new required
     * properties, adds them and recurses.
     */
    private ObjectSchema resolveEffectiveSchema(Set<String> selectedProperties) {
        var depSchemas = schema.getDependentSchemas();
        var toMerge = new ArrayList<Schema>();
        toMerge.add(schema);
        for (var property : selectedProperties) {
            var depSchema = depSchemas.get(property);
            if (depSchema != null) {
                toMerge.add(depSchema);
            }
        }
        var merged = SchemaMerger.merge(toMerge);
        if (merged instanceof ObjectSchema mergedObj) {
            if (selectedProperties.addAll(mergedObj.getRequired())) {
                return resolveEffectiveSchema(selectedProperties);
            }
            return mergedObj;
        }
        return schema;
    }
}
