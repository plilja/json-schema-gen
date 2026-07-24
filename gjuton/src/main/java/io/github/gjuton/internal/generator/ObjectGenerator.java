package io.github.gjuton.internal.generator;

import static io.github.gjuton.internal.generator.GenerationResult.result;
import static io.github.gjuton.internal.util.FunctionalUtil.coalesce;

import com.github.curiousoddman.rgxgen.RgxGen;
import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.model.ObjectSchema;
import io.github.gjuton.internal.model.Schema;
import io.github.gjuton.internal.model.UnsatisfiableSchema;
import io.github.gjuton.internal.model.UntypedSchema;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final List<String> requiredAndTransitiveRequired;
    private final Map<Pattern, Schema> compiledPatternProperties;
    private final Map<String, RgxGen> patternGenerators;

    /**
     * The schema {@link #synthesizableSchema} falls back to when
     * {@code additionalProperties} is absent or {@code true}. Fixed for this
     * generator's lifetime so every synthesized property shares one generator
     * instance, accumulating phase and novelty state across them instead of
     * restarting fresh each time.
     */
    private final Schema untypedFallback = new UntypedSchema();

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
        var allRequired = new LinkedHashSet<String>();
        for (var req : schema.getRequired()) {
            allRequired.addAll(computeImpliedProperties(req, schema.getDependentRequired(), schema.getDependentSchemas()));
        }
        this.requiredAndTransitiveRequired = List.copyOf(allRequired);
    }

    /**
     * Returns all properties that must be present when {@code property}
     * is present, including {@code property} itself. The result reflects
     * {@code dependentRequired} and {@code dependentSchemas} constraints
     * transitively.
     */
    static Set<String> computeImpliedProperties(String property,
                                              Map<String, List<String>> dependentRequired,
                                              Map<String, Schema> dependentSchemas) {
        var closure = new LinkedHashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(property);
        var depRequired = new HashMap<>(dependentRequired);

        while (!queue.isEmpty()) {
            var prop = queue.poll();
            if (!closure.add(prop)) {
                continue;
            }
            for (var dependent : depRequired.getOrDefault(prop, List.of())) {
                if (!closure.contains(dependent)) {
                    queue.add(dependent);
                }
            }
            var depSchema = dependentSchemas.get(prop);
            if (depSchema instanceof ObjectSchema depObj) {
                for (var req : depObj.getRequired()) {
                    if (!closure.contains(req)) {
                        queue.add(req);
                    }
                }
                for (var entry : depObj.getDependentRequired().entrySet()) {
                    depRequired.merge(entry.getKey(), entry.getValue(), (a, b) -> {
                        var combined = new ArrayList<>(a);
                        combined.addAll(b);
                        return combined;
                    });
                    if (closure.contains(entry.getKey())) {
                        for (var dep : entry.getValue()) {
                            if (!closure.contains(dep)) {
                                queue.add(dep);
                            }
                        }
                    }
                }
            }
        }
        return closure;
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
        if (current == GenerationPhase.FOCUS && focusCursor < satisfiableOptionalProperties().size()) {
            return GenerationPhase.FOCUS;
        }
        return super.advanceToNext(current);
    }

    @Override
    protected GenerationResult<Map<String, Object>> generatePhase(GenerationPhase phase) {
        var optionalProperties = satisfiableOptionalProperties();
        advancePastExhaustedFocusProperties(optionalProperties);
        if (phase == GenerationPhase.FOCUS && focusCursor >= optionalProperties.size()) {
            return GenerationResult.skip();
        }
        int requiredCount = requiredAndTransitiveRequired.size();
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

        var focusProperty = phase == GenerationPhase.FOCUS ? optionalProperties.get(focusCursor) : null;
        LinkedHashSet<String> selected;
        try {
            selected = selectProperties(optionalProperties, targetCount, effectiveMax, focusProperty);
        } catch (UnsatisfiableSchemaException e) {
            // Forcing the focused property proved impossible; skip it so later FOCUS
            // attempts move on instead of re-targeting the same property every retry.
            if (focusProperty != null) {
                focusCursor++;
            }
            throw e;
        }
        return result(generateSelected(selected, targetCount, effectiveMin));
    }

    /**
     * Advances {@link #focusCursor} past every optional property at the front
     * of {@code optionalProperties} whose generator's novelty score, as of
     * the last completed run, has already dropped to zero - so the
     * {@code FOCUS} phase spends no further attempts on a property that has
     * nothing left to give. A property never yet visited reports an empty
     * score rather than zero, so it is left in place instead of being
     * skipped before it gets a first attempt.
     */
    private void advancePastExhaustedFocusProperties(List<String> optionalProperties) {
        while (focusCursor < optionalProperties.size()) {
            var focusPropertyGenerator = fieldGenerator(optionalProperties.get(focusCursor));
            var focusPropertyNoveltyScore = context.noveltyScore(focusPropertyGenerator);
            if (focusPropertyNoveltyScore.filter(score -> score == 0.0).isEmpty()) {
                return;
            }
            focusCursor++;
        }
    }

    private Generator<?> fieldGenerator(String property) {
        return context.generatorFor(resolveFieldSchema(schema, property)).delegate();
    }

    /**
     * Selects which properties to include. All transitively required
     * properties are included unconditionally. Optional properties are
     * included with all properties they transitively depend on, as long
     * as the total fits within {@code targetCount}.
     *
     * <p>A non-null {@code focusProperty} is included ahead of the
     * {@code targetCount} gate, bounded only by the schema's {@code maxProperties}
     * — so a property whose mandatory dependencies would otherwise exceed the
     * phase target still appears, and is omitted only when {@code maxProperties}
     * makes it impossible.
     */
    private LinkedHashSet<String> selectProperties(List<String> optionalProperties,
                                                   int targetCount,
                                                   int effectiveMax,
                                                   String focusProperty) {
        var selected = new LinkedHashSet<>(requiredAndTransitiveRequired);

        if (selected.size() > effectiveMax) {
            throw new UnsatisfiableSchemaException(
                    "Required properties with dependentRequired (" + selected.size()
                            + ") exceed maxProperties (" + schema.getMaxProperties() + ")");
        }

        if (focusProperty != null && !selected.contains(focusProperty)) {
            var closure = computeImpliedProperties(focusProperty, schema.getDependentRequired(), schema.getDependentSchemas());
            var tentative = new LinkedHashSet<>(selected);
            tentative.addAll(closure);
            Integer maxProperties = schema.getMaxProperties();
            if (maxProperties == null || tentative.size() <= maxProperties) {
                selected = tentative;
            }
        }

        for (var property : optionalProperties) {
            if (selected.size() >= targetCount) {
                break;
            }
            if (!selected.contains(property)) {
                var closure = computeImpliedProperties(property, schema.getDependentRequired(), schema.getDependentSchemas());
                var tentative = new LinkedHashSet<>(selected);
                tentative.addAll(closure);
                if (tentative.size() <= targetCount) {
                    selected = tentative;
                }
            }
        }

        return selected;
    }

    /**
     * Generates a JSON object containing the given properties, using
     * the effective schema for value generation and synthesizing
     * additional properties if needed to reach {@code targetCount}.
     */
    private Map<String, Object> generateSelected(Set<String> selected, int targetCount, int effectiveMin) {
        var effectiveSchema = resolveEffectiveSchema(selected);
        var obj = new LinkedHashMap<String, Object>();
        for (var property : selected) {
            var segment = "." + property;
            var value = JsonGenerator.generateForPath(context, segment, () -> resolveFieldSchema(effectiveSchema, property));
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
     * Returns the schema that applies when the given properties are
     * present — the base schema narrowed by any {@code dependentSchemas}
     * constraints the selection triggers.
     */
    private ObjectSchema resolveEffectiveSchema(Set<String> selectedProperties) {
        var schemas = new ArrayList<Schema>();
        schemas.add(schema);
        for (var property : selectedProperties) {
            var depSchema = schema.getDependentSchemas().get(property);
            if (depSchema != null) {
                schemas.add(depSchema);
            }
        }
        var merged = context.mergedSchema(schemas);
        if (merged instanceof ObjectSchema mergedObj) {
            return mergedObj;
        }
        return schema;
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
        return context.mergedSchema(toMerge);
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
     * If absent or {@code true}, returns {@link #untypedFallback}.
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
        return untypedFallback;
    }

    /**
     * Returns the satisfiable optional properties, filtering out
     * required properties and unsatisfiable schemas.
     */
    private List<String> satisfiableOptionalProperties() {
        var result = new ArrayList<String>();
        for (var property : schema.getProperties().keySet()) {
            if (!requiredAndTransitiveRequired.contains(property)
                    && !(schema.getProperties().get(property) instanceof UnsatisfiableSchema)) {
                result.add(property);
            }
        }
        return result;
    }
}
