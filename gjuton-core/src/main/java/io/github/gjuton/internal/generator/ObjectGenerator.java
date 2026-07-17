package io.github.gjuton.internal.generator;

import static io.github.gjuton.internal.generator.GenerationResult.result;
import static io.github.gjuton.internal.util.CollectionUtil.reversed;
import static io.github.gjuton.internal.util.FunctionalUtil.coalesce;

import com.github.curiousoddman.rgxgen.RgxGen;
import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.ObjectSchema;
import io.github.gjuton.internal.model.Schema;
import io.github.gjuton.internal.model.UnsatisfiableSchema;
import io.github.gjuton.internal.model.UntypedSchema;
import io.github.gjuton.internal.util.GraphUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generator for {@code "type": "object"} schemas. Varies the number of
 * included properties across successive calls to cover the allowed range
 * defined by {@code minProperties} and {@code maxProperties}.
 */
final class ObjectGenerator extends PhaseGenerator<ObjectGenerator.GenerationPhase, Map<String, Object>> {

    private static final int PATTERN_NAME_RETRY_BUDGET = 20;

    /**
     * How many synthesized properties may appear above the schema's named set
     * when the caller opts into additional properties and {@code maxProperties}
     * imposes no lower ceiling.
     */
    private static final int ADDITIONAL_PROPERTIES_HEADROOM = 3;

    private final ObjectSchema schema;
    private final Map<Pattern, Schema> compiledPatternProperties;
    private final Map<String, RgxGen> patternGenerators;

    enum GenerationPhase {
        MIN_PROPERTIES, MAX_PROPERTIES, FOCUS, RANDOM
    }

    /**
     * Index into the optional-property list of the property the {@code FOCUS}
     * phase is currently driving toward full coverage. Advances only once that
     * property is fully covered or proves impossible to place, so every optional
     * property — including one a fixed selection order would never reach under
     * {@code maxProperties} — eventually gets exercised.
     */
    private int focusCursor;

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

    /**
     * Keeps the {@code FOCUS} phase in effect until every optional property has
     * been driven to full coverage or skipped as unplaceable, so one pass
     * through the phases still exercises all of them before generation settles
     * into purely random values.
     */
    @Override
    protected GenerationPhase advanceToNext(GenerationPhase current) {
        if (current == GenerationPhase.FOCUS && !allOptionalPropertiesFocused()) {
            return GenerationPhase.FOCUS;
        }
        return super.advanceToNext(current);
    }

    private boolean allOptionalPropertiesFocused() {
        var order = reverseTopologicalOrderDependentProperties();
        var optionalProperties = satisfiableOptionalProperties(order);
        return focusCursor >= optionalProperties.size();
    }

    /**
     * A declared property is generated from its own schema instance only when no
     * {@code patternProperties} regex also constrains it; otherwise the value
     * comes from a per-call merge whose coverage folds into this object's phase
     * counts instead.
     */
    @Override
    public List<Schema> structuralChildren() {
        var children = new ArrayList<Schema>();
        for (var entry : schema.getProperties().entrySet()) {
            if (!hasMatchingPatternProperty(entry.getKey())) {
                children.add(entry.getValue());
            }
        }
        return children;
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

        // When the caller opts into additional properties and the schema places no
        // constraint on extra fields, lift the ceiling so synthesized properties
        // beyond the named set can appear (still bounded by maxProperties).
        boolean addExtraProperties = context.generateAdditionalProperties()
                && synthesizableSchema() instanceof UntypedSchema;
        if (addExtraProperties) {
            int hardMax = coalesce(schema.getMaxProperties(), Integer.MAX_VALUE);
            effectiveMax = Math.min(hardMax, effectiveMax + ADDITIONAL_PROPERTIES_HEADROOM);
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
            // FOCUS fills minimally so the focused property's own generator is
            // exercised in isolation, not shadowed by a dependent schema that a
            // larger fill would pull in and merge over it.
            case MIN_PROPERTIES, FOCUS -> effectiveMin;
            case MAX_PROPERTIES -> effectiveMax;
            case RANDOM -> effectiveMin + context.random().nextInt(effectiveMax - effectiveMin + 1);
        };

        var focusProperty = phase == GenerationPhase.FOCUS ? focusedProperty(optionalProperties) : null;
        Map<String, Object> generated;
        try {
            generated = selectAndResolve(order, optionalProperties, targetCount, effectiveMin, effectiveMax, focusProperty);
        } catch (UnsatisfiableSchemaException e) {
            // Forcing the focused property proved impossible; skip it so later FOCUS
            // attempts move on instead of re-targeting the same property every retry.
            if (focusProperty != null) {
                focusCursor++;
            }
            throw e;
        }
        if (phase == GenerationPhase.FOCUS) {
            advanceFocus(optionalProperties, generated);
        }
        return result(generated);
    }

    /**
     * The optional property the {@code FOCUS} phase should include in this
     * object, or {@code null} when every optional property has already been
     * covered or the schema declares none.
     */
    private String focusedProperty(List<String> optionalProperties) {
        if (focusCursor >= optionalProperties.size()) {
            return null;
        }
        return optionalProperties.get(focusCursor);
    }

    /**
     * Moves the {@code FOCUS} phase on to the next optional property once the
     * current one is either fully covered or could not be placed within
     * {@code maxProperties}; otherwise leaves it in place so the next
     * {@code FOCUS} phase keeps exercising it.
     */
    private void advanceFocus(List<String> optionalProperties, Map<String, Object> generated) {
        var focusProperty = focusedProperty(optionalProperties);
        if (focusProperty == null) {
            return;
        }
        boolean placed = generated.containsKey(focusProperty);
        if (!placed || isFullyCovered(focusProperty)) {
            focusCursor++;
        }
    }

    /**
     * Whether {@code property}'s own generator has emitted all of its deliberate
     * values. Considers only the property's direct generator, not nested
     * descendants, so a deeply nested subtree may still be incomplete when this
     * returns {@code true}.
     */
    private boolean isFullyCovered(String property) {
        var fieldSchema = resolveFieldSchema(schema, property);
        var generator = context.generatorFor(fieldSchema);
        return generator.emittedCount() >= generator.totalCount();
    }

    /**
     * Selects properties and resolves dependent schemas in one pass,
     * returning the generated object. Required properties are added first
     * with full transitive resolution; optional properties are added
     * tentatively and kept only when the resolved set fits within
     * {@code targetCount}.
     *
     * <p>A non-null {@code focusProperty} is included ahead of the
     * {@code targetCount} gate, bounded only by the schema's {@code maxProperties}
     * - so a property whose mandatory dependencies would otherwise exceed the
     * phase target still appears, and is omitted only when {@code maxProperties}
     * makes it impossible.
     */
    private Map<String, Object> selectAndResolve(List<String> order,
                                                 List<String> optionalProperties,
                                                 int targetCount,
                                                 int effectiveMin,
                                                 int effectiveMax,
                                                 String focusProperty) {
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

        if (focusProperty != null && !selected.contains(focusProperty)) {
            var tentative = new LinkedHashSet<>(selected);
            var tentativeSchema = resolveProperty(effectiveSchema, focusProperty, tentative);
            Integer maxProperties = schema.getMaxProperties();
            if (maxProperties == null || tentative.size() <= maxProperties) {
                selected = tentative;
                effectiveSchema = tentativeSchema;
            }
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
        var schemaForFields = effectiveSchema;
        for (var property : selected) {
            var segment = "." + property;
            var value = JsonGenerator.generateForPath(context, segment, () -> resolveFieldSchema(schemaForFields, property));
            obj.put(property, value);
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
        for (var dependent : schema.getDependentRequired().getOrDefault(property, List.of())) {
            if (!selected.contains(dependent)) {
                current = resolveProperty(current, dependent, selected);
            }
        }

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
