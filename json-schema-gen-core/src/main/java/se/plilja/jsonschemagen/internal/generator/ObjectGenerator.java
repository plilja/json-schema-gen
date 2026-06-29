package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;
import static se.plilja.jsonschemagen.internal.util.CollectionUtil.reversed;
import static se.plilja.jsonschemagen.internal.util.FunctionalUtil.coalesce;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.model.UnsatisfiableSchema;
import se.plilja.jsonschemagen.internal.model.UntypedSchema;
import se.plilja.jsonschemagen.internal.util.GraphUtil;

/**
 * Generator for {@code "type": "object"} schemas. Varies the number of
 * included properties across successive calls to cover the allowed range
 * defined by {@code minProperties} and {@code maxProperties}.
 */
final class ObjectGenerator extends PhaseGenerator<ObjectGenerator.GenerationPhase, Map<String, Object>> {

    private final ObjectSchema schema;

    enum GenerationPhase {
        MIN_PROPERTIES, MAX_PROPERTIES, RANDOM
    }

    ObjectGenerator(GeneratorContext context, ObjectSchema schema) {
        super(GenerationPhase.class, context);
        this.schema = schema;
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.MIN_PROPERTIES;
    }

    @Override
    protected GenerationResult<Map<String, Object>> generatePhase(GenerationPhase phase) {
        var order = reverseTopologicalOrderDependentProperties();
        var optionalProperties = satisfiableOptionalProperties(order);
        int requiredCount = schema.getRequired().size();
        int effectiveMin = Math.max(requiredCount, coalesce(schema.getMinProperties(), 0));
        int numberOfNamedProperties = requiredCount + optionalProperties.size();
        int effectiveMax;
        if (canSynthesizeNewProperties()) {
            // We can invent as many new properties as needed, up to maxProperties
            effectiveMax = coalesce(schema.getMaxProperties(), Math.max(numberOfNamedProperties, effectiveMin));
        } else {
            // Only named properties are allowed
            effectiveMax = Math.min(numberOfNamedProperties, coalesce(schema.getMaxProperties(), Integer.MAX_VALUE));
        }

        if (effectiveMax < requiredCount) {
            throw new UnsatisfiableSchemaException(
                    "maxProperties (" + schema.getMaxProperties() + ") is less than required property count (" + requiredCount + ")");
        }
        if (effectiveMin > effectiveMax) {
            throw new UnsatisfiableSchemaException(
                    "minProperties (" + schema.getMinProperties() + ") exceeds the number of satisfiable properties (" + effectiveMax + ")");
        }

        int targetCount = switch (phase) {
            case MIN_PROPERTIES -> effectiveMin;
            case MAX_PROPERTIES -> effectiveMax;
            case RANDOM -> effectiveMin + context.random().nextInt(effectiveMax - effectiveMin + 1);
        };

        return result(selectAndResolve(order, optionalProperties, targetCount, effectiveMin, effectiveMax));
    }

    /**
     * Selects properties and resolves dependent schemas in one pass,
     * returning the generated object. Required properties are added first
     * with full transitive resolution; optional properties are added
     * tentatively and kept only when the resolved set fits within
     * {@code targetCount}.
     */
    private Map<String, Object> selectAndResolve(List<String> order,
                                                 List<String> optionalProperties,
                                                 int targetCount,
                                                 int effectiveMin,
                                                 int effectiveMax) {
        var selected = new LinkedHashSet<String>();
        var effectiveSchema = schema;

        // Add required properties, resolving dependentRequired and dependentSchemas
        for (var property : order) {
            if (schema.getRequired().contains(property)) {
                effectiveSchema = resolveProperty(effectiveSchema, property, selected);
            }
        }

        if (selected.size() > effectiveMax) {
            throw new UnsatisfiableSchemaException(
                    "Required properties with dependentRequired (" + selected.size()
                            + ") exceed maxProperties (" + schema.getMaxProperties() + ")");
        }

        // Add optional properties, tentatively resolving to check fit
        for (var property : optionalProperties) {
            if (selected.size() >= targetCount) {
                break;
            }
            if (!selected.contains(property)) {
                var tentative = new LinkedHashSet<>(selected);
                var tentativeSchema = resolveProperty(effectiveSchema, property, tentative);
                if (tentative.size() <= targetCount) {
                    selected = tentative;
                    effectiveSchema = tentativeSchema;
                }
            }
        }

        var obj = new LinkedHashMap<String, Object>();
        for (var property : selected) {
            var fieldSchema = effectiveSchema.getProperties().get(property);
            if (fieldSchema != null) {
                obj.put(property, context.generatorFor(fieldSchema).generate());
            }
        }
        // Synthesize additional properties to reach targetCount
        var synthesizeSchema = synthesizableSchema();
        if (obj.size() < targetCount && synthesizeSchema != null) {
            int i = 0;
            while (obj.size() < targetCount) {
                var name = "prop" + i++;
                if (!obj.containsKey(name)) {
                    obj.put(name, context.generatorFor(synthesizeSchema).generate());
                }
            }
        }
        if (obj.size() < effectiveMin) {
            throw new UnsatisfiableSchemaException(
                    "Could not select enough properties to satisfy minProperties (" + schema.getMinProperties()
                            + "); only " + obj.size() + " satisfiable properties fit within constraints");
        }
        return obj;
    }

    /**
     * Adds a property to {@code selected}, resolves its
     * {@code dependentRequired} and {@code dependentSchemas}, and
     * transitively resolves any newly required properties introduced
     * by the merge.
     *
     * @return the effective schema after merging triggered dependent schemas
     */
    private ObjectSchema resolveProperty(ObjectSchema current, String property, LinkedHashSet<String> selected) {
        selected.add(property);
        selected.addAll(schema.getDependentRequired().getOrDefault(property, List.of()));

        var depSchema = schema.getDependentSchemas().get(property);
        if (depSchema != null) {
            var merged = SchemaMerger.merge(List.of(current, depSchema));
            if (merged instanceof ObjectSchema mergedObj) {
                current = mergedObj;
            }
        }

        for (var req : current.getRequired()) {
            if (!selected.contains(req)) {
                current = resolveProperty(current, req, selected);
            }
        }
        return current;
    }

    /**
     * Whether the generator can invent new property names beyond those
     * declared in {@code properties}. True unless {@code additionalProperties}
     * is {@code false}.
     */
    private boolean canSynthesizeNewProperties() {
        return synthesizableSchema() != null;
    }

    /**
     * Returns the schema for generating synthesized property values.
     * If {@code additionalProperties} is a schema, returns that schema.
     * If absent or {@code true}, returns an {@link UntypedSchema}.
     * If {@code false}, returns {@code null} (synthesis is not possible).
     */
    private Schema synthesizableSchema() {
        var additional = schema.getAdditionalProperties();
        if (additional instanceof Schema s) {
            return s;
        }
        if (Boolean.FALSE.equals(additional)) {
            return null;
        }
        return new UntypedSchema();
    }

    /**
     * Orders the properties so that if property A has a dependentRequired on
     * property B, then B comes before A in the returned list.
     */
    private List<String> reverseTopologicalOrderDependentProperties() {
        var depRequired = schema.getDependentRequired();
        var properties = new ArrayList<>(schema.getProperties().keySet());
        return reversed(GraphUtil.topologicalSort(properties, depRequired));
    }

    /**
     * Returns the satisfiable optional properties, preserving the input order
     * and filtering out required properties and unsatisfiable schemas.
     */
    private List<String> satisfiableOptionalProperties(List<String> order) {
        var result = new ArrayList<String>();
        for (var property : order) {
            if (!schema.getRequired().contains(property)
                    && !(schema.getProperties().get(property) instanceof UnsatisfiableSchema)) {
                result.add(property);
            }
        }
        return result;
    }
}
