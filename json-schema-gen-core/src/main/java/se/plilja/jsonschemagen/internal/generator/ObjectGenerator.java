package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;
import static se.plilja.jsonschemagen.internal.util.CollectionUtil.reversed;
import static se.plilja.jsonschemagen.internal.util.FunctionalUtil.coalesce;

import com.github.curiousoddman.rgxgen.RgxGen;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

    private static final int PATTERN_NAME_RETRY_BUDGET = 20;

    private final ObjectSchema schema;
    private final Map<Pattern, Schema> compiledPatternProperties;
    private final Map<String, RgxGen> patternGenerators;

    enum GenerationPhase {
        MIN_PROPERTIES, MAX_PROPERTIES, RANDOM
    }

    ObjectGenerator(GeneratorContext context, ObjectSchema schema) {
        super(GenerationPhase.class, context);
        this.schema = schema;
        this.compiledPatternProperties = schema.getPatternProperties().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> Pattern.compile(e.getKey()),
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
        this.patternGenerators = schema.getPatternProperties().keySet().stream()
                .collect(Collectors.toMap(
                        k -> k,
                        RgxGen::parse,
                        (a, b) -> a,
                        LinkedHashMap::new));
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
            var fieldSchema = resolveFieldSchema(effectiveSchema, property);
            obj.put(property, context.generatorFor(fieldSchema).generate());
        }
        // Synthesize additional properties to reach targetCount
        var synthesizeSchema = synthesizableSchema();
        if (obj.size() < targetCount && synthesizeSchema != null) {
            int i = 0;
            while (obj.size() < targetCount) {
                var name = "prop" + i++;
                if (!obj.containsKey(name)) {
                    obj.put(name, context.generatorFor(withMatchingPatternSchemas(synthesizeSchema, name)).generate());
                }
            }
        } else if (obj.size() < targetCount && !effectiveSchema.getPatternProperties().isEmpty()) {
            synthesizeFromPatterns(obj, targetCount);
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
     * Merges {@code fieldSchema} with the schema of every
     * {@code patternProperties} entry whose regex matches {@code property},
     * so the generated value satisfies both {@code properties} and
     * {@code patternProperties} constraints.
     */
    private Schema withMatchingPatternSchemas(Schema fieldSchema, String property) {
        var toMerge = new ArrayList<Schema>();
        toMerge.add(fieldSchema);
        for (var entry : compiledPatternProperties.entrySet()) {
            if (entry.getKey().matcher(property).find()) {
                toMerge.add(entry.getValue());
            }
        }
        return SchemaMerger.merge(toMerge);
    }

    /**
     * Resolves the schema used to generate {@code property}'s value, applying
     * JSON Schema's {@code properties}/{@code patternProperties}/{@code additionalProperties}
     * precedence. This lets a {@code required} property that has no entry in
     * {@code properties} still be generated.
     *
     * @throws UnsatisfiableSchemaException if none of those sources apply,
     *         i.e. {@code additionalProperties} is {@code false} and no
     *         {@code patternProperties} regex matches {@code property}
     */
    private Schema resolveFieldSchema(ObjectSchema effectiveSchema, String property) {
        var fieldSchema = effectiveSchema.getProperties().get(property);
        if (fieldSchema != null) {
            return withMatchingPatternSchemas(fieldSchema, property);
        }
        // No declared schema - try patternProperties before falling back
        if (hasMatchingPatternProperty(property)) {
            return withMatchingPatternSchemas(new UntypedSchema(), property);
        }
        // No pattern match either - additionalProperties is the last resort
        var fallback = synthesizableSchema();
        if (fallback == null) {
            throw new UnsatisfiableSchemaException(
                    "Property '" + property + "' is required but has no schema in properties or patternProperties, "
                            + "and additionalProperties is false");
        }
        return fallback;
    }

    private boolean hasMatchingPatternProperty(String property) {
        return compiledPatternProperties.keySet().stream().anyMatch(pattern -> pattern.matcher(property).find());
    }

    /**
     * Synthesizes properties by generating names from {@code patternProperties}
     * regexes, for use when {@code additionalProperties} is {@code false} but
     * patterns can still supply fresh names.
     *
     * @throws UnsatisfiableSchemaException if distinct matching names cannot be
     *         generated within the retry budget
     */
    private void synthesizeFromPatterns(Map<String, Object> obj, int targetCount) {
        var generators = new ArrayList<>(patternGenerators.values());
        int collisions = 0;
        int generatorIndex = 0;
        while (obj.size() < targetCount) {
            if (collisions >= PATTERN_NAME_RETRY_BUDGET) {
                throw new UnsatisfiableSchemaException(
                        "Could not synthesize enough distinct property names matching patternProperties to satisfy minProperties ("
                                + schema.getMinProperties() + ")");
            }
            var name = generators.get(generatorIndex % generators.size()).generate(context.random());
            generatorIndex++;
            if (obj.containsKey(name)) {
                collisions++;
            } else {
                obj.put(name, context.generatorFor(withMatchingPatternSchemas(new UntypedSchema(), name)).generate());
            }
        }
    }

    /**
     * Whether the generator can invent new property names beyond those
     * declared in {@code properties}. True unless {@code additionalProperties}
     * is {@code false} and no {@code patternProperties} pattern can supply names.
     */
    private boolean canSynthesizeNewProperties() {
        return synthesizableSchema() != null // We have a schema that we can synthesize properties from
            || !schema.getPatternProperties().isEmpty(); // We can synthesize properties that match pattern properties
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
        var properties = new LinkedHashSet<>(schema.getProperties().keySet());
        properties.addAll(schema.getRequired());
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
