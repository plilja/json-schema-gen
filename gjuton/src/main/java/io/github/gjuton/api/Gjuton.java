package io.github.gjuton.api;

import io.github.gjuton.errors.JsonBindingException;
import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.generator.GeneratorConfig;
import io.github.gjuton.internal.generator.JsonGenerator;
import io.github.gjuton.internal.generator.ValueConstraints;
import io.github.gjuton.internal.model.SchemaDocument;
import io.github.gjuton.internal.parser.JsonSerializer;
import io.github.gjuton.internal.parser.SchemaParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Generates valid JSON values from a JSON Schema document.
 *
 * <p>Instances are not thread-safe. Each thread should use its own generator.
 *
 * <p>The {@code with*} methods return a new generator with the updated
 * configuration; the original is unchanged. Each overrides any prior call to
 * the same setter (last call wins).
 *
 * <pre>{@code
 * Gjuton gen = Gjuton.of(schema);
 * String json1 = gen.generate();
 * String json2 = gen.generate();
 * }</pre>
 */
public final class Gjuton {

    private final String schema;
    private final Long seed;
    private final Map<String, ValueProducer> producers;
    private final GenerationMode mode;
    private final boolean generateAdditionalProperties;
    private final int refSoftDepth;
    private final int refHardDepth;
    private final Constraints constraints;
    private final JsonGenerator generator;
    private final SchemaDocument document;

    private Gjuton(
            String schema,
            SchemaDocument document,
            Long seed,
            Map<String, ValueProducer> producers,
            GenerationMode mode,
            boolean generateAdditionalProperties,
            int refSoftDepth,
            int refHardDepth,
            Constraints constraints) {
        this.schema = schema;
        this.document = document;
        this.seed = seed;
        this.producers = producers;
        this.mode = mode;
        this.generateAdditionalProperties = generateAdditionalProperties;
        this.refSoftDepth = refSoftDepth;
        this.refHardDepth = refHardDepth;
        this.constraints = constraints;
        var config = new GeneratorConfig(
                mode == GenerationMode.RANDOM,
                generateAdditionalProperties,
                refSoftDepth,
                refHardDepth,
                toValueSuppliers(producers),
                toValueConstraints(constraints));
        this.generator = new JsonGenerator(seed, document, config);
    }

    /**
     * Adapts the public {@link ValueProducer} map into a {@link Supplier} map.
     * The internal generator layer cannot depend on the {@code api} package,
     * so it consumes each producer as a plain {@link Supplier}.
     */
    private static Map<String, Supplier<Object>> toValueSuppliers(Map<String, ValueProducer> producers) {
        return producers.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue()::produce));
    }

    /**
     * Adapts the public {@link Constraints} into the internal generator record.
     * The generator layer cannot depend on the {@code api} package, so it
     * carries the bounds as a plain record of JDK-typed values.
     */
    private static ValueConstraints toValueConstraints(Constraints constraints) {
        return new ValueConstraints(
                constraints.stringMinLength,
                constraints.stringMaxLength,
                constraints.numberMin,
                constraints.numberMax,
                constraints.dateMin,
                constraints.dateMax,
                constraints.alphabet,
                constraints.arrayMinLength,
                constraints.arrayMaxLength);
    }

    /**
     * Creates a generator for the given JSON Schema string.
     *
     * @param schema a JSON Schema document as a UTF-8 string
     */
    public static Gjuton of(String schema) {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        var document = SchemaParser.parse(schema);
        return new Gjuton(
                schema, document, null, Collections.emptyMap(),
                GenerationMode.RANDOM, false, GeneratorConfig.DEFAULT_REF_SOFT_DEPTH, GeneratorConfig.DEFAULT_REF_HARD_DEPTH,
                Constraints.of());
    }

    /**
     * Creates a generator by reading a JSON Schema from a file.
     *
     * <p>External {@code $ref} values (relative file paths or HTTP URLs) are
     * resolved relative to the file's parent directory.
     *
     * @param schema file containing a JSON Schema document in UTF-8 encoding
     */
    public static Gjuton of(File schema) throws IOException {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        var schemaString = Files.readString(schema.toPath());
        var document = SchemaParser.parse(schema.toPath().toAbsolutePath());
        return new Gjuton(
                schemaString, document, null, Collections.emptyMap(),
                GenerationMode.RANDOM, false, GeneratorConfig.DEFAULT_REF_SOFT_DEPTH, GeneratorConfig.DEFAULT_REF_HARD_DEPTH,
                Constraints.of());
    }

    /**
     * Creates a generator by reading a JSON Schema from an input stream.
     * The stream is read to completion but not closed.
     *
     * @param schema stream containing a JSON Schema document in UTF-8 encoding
     */
    public static Gjuton of(InputStream schema) throws IOException {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        return of(new String(schema.readAllBytes(), StandardCharsets.UTF_8));
    }

    /**
     * Returns a new generator with the given seed. Two generators with the same
     * schema and seed produce the same sequence of values across repeated calls
     * to {@link #generate()}.
     *
     * @param seed value used to initialise the random source
     */
    public Gjuton withSeed(long seed) {
        return new Gjuton(
                schema, document, seed, producers, mode, generateAdditionalProperties, refSoftDepth, refHardDepth, constraints);
    }

    /**
     * Returns a new generator that overrides the value at {@code jsonPath}
     * with whatever {@code producer} returns instead of generating it from the
     * schema; a later producer at the same path replaces an earlier one.
     *
     * <p>The producer fires once per {@link #generate()} call that visits the
     * path, and not at all on calls that don't reach it. The overridden subtree
     * is never generated — so a path whose schema is unsatisfiable or
     * unsupported can still be populated — and the value is inserted as-is,
     * without validation against the schema.
     *
     * <p>Paths match exactly (no wildcards): {@code $} root, {@code $.a.b} a
     * nested field, {@code $.items[0]} an array element.
     *
     * <pre>{@code
     * Gjuton gen = Gjuton.of(schema)
     *         // a fixed value: pin a user id that exists in the test database
     *         .withProducer("$.userId", () -> 42)
     *         // a fresh value each generate() call, composed with a data faker
     *         .withProducer("$.email", faker.internet()::emailAddress);
     *
     * String json = gen.generate();
     * }</pre>
     *
     * @param jsonPath path identifying the position to override
     * @param producer supplies the value placed at that position
     * @see ValueProducer
     */
    public Gjuton withProducer(String jsonPath, ValueProducer producer) {
        if (jsonPath == null) {
            throw new IllegalArgumentException("jsonPath must not be null");
        }
        if (producer == null) {
            throw new IllegalArgumentException("producer must not be null");
        }
        var merged = new LinkedHashMap<>(producers);
        merged.put(jsonPath, producer);
        return new Gjuton(
                schema, document, seed, Collections.unmodifiableMap(merged),
                mode, generateAdditionalProperties, refSoftDepth, refHardDepth, constraints);
    }

    /**
     * Returns a new generator using the given generation mode. Overrides any
     * mode set by a previous call. Defaults to {@link GenerationMode#RANDOM}.
     *
     * @param mode the strategy for choosing values across successive
     *     {@link #generate()} calls
     */
    public Gjuton withGenerationMode(GenerationMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        return new Gjuton(
                schema, document, seed, producers, mode, generateAdditionalProperties, refSoftDepth, refHardDepth, constraints);
    }

    /**
     * Returns a new generator that adds random extra properties to generated
     * objects wherever the schema permits them (i.e. {@code additionalProperties}
     * is absent or {@code true}), to exercise consumers that do not expect
     * unknown fields. Off by default.
     */
    public Gjuton withAdditionalProperties() {
        return new Gjuton(
                schema, document, seed, producers, mode, true, refSoftDepth, refHardDepth, constraints);
    }

    /**
     * Returns a new generator that expands {@code $ref} chains shallowly,
     * favouring compact output over deeply nested structure. Overrides any
     * recursion limits set by a previous call.
     */
    public Gjuton withRecursionLimitsShallow() {
        return withRecursionLimits(GeneratorConfig.SHALLOW_REF_SOFT_DEPTH, GeneratorConfig.SHALLOW_REF_HARD_DEPTH);
    }

    /**
     * Returns a new generator that expands {@code $ref} chains deeply, for
     * schemas with legitimately deep nesting. Overrides any recursion limits
     * set by a previous call.
     */
    public Gjuton withRecursionLimitsDeep() {
        return withRecursionLimits(GeneratorConfig.DEEP_REF_SOFT_DEPTH, GeneratorConfig.DEEP_REF_HARD_DEPTH);
    }

    /**
     * Returns a new generator using the given {@code $ref} expansion ceilings,
     * overriding any recursion limits set by a previous call. At the soft
     * ceiling recursive structures collapse to their smallest valid form so
     * generation terminates; at the hard ceiling a {@code $ref} that still has
     * not bottomed out is treated as unsatisfiable and generation fails.
     *
     * <p>When unset, the default {@code $ref} depth limits apply.
     *
     * @param soft depth at which recursive structures collapse to their
     *     smallest valid form; must be {@code >= 1} and {@code <= hard}
     * @param hard depth beyond which a still-recursing {@code $ref} is
     *     unsatisfiable
     * @throws IllegalArgumentException if {@code soft < 1} or {@code soft > hard}
     */
    public Gjuton withRecursionLimits(int soft, int hard) {
        if (soft < 1) {
            throw new IllegalArgumentException("soft limit must be at least 1, was " + soft);
        }
        if (soft > hard) {
            throw new IllegalArgumentException(
                    "soft limit (" + soft + ") must not exceed hard limit (" + hard + ")");
        }
        return new Gjuton(
                schema, document, seed, producers, mode, generateAdditionalProperties, soft, hard, constraints);
    }

    /**
     * Returns a new generator that narrows generated values to {@code constraints},
     * on top of each schema node's own constraints, overriding any set by a
     * previous call (last call wins). Every value kind left unset in
     * {@code constraints} keeps its schema-driven behaviour.
     *
     * <p>A bound only ever tightens: at each position the effective range is the
     * intersection of the schema's constraint and the matching bound, so a bound
     * looser than the schema has no effect and one stricter replaces it. A
     * position whose intersection admits no value fails generation with
     * {@link UnsatisfiableSchemaException}, like any over-constrained schema.
     *
     * <pre>{@code
     * String json = Gjuton.of(schema)
     *         .withConstraints(Constraints.of()
     *                 .stringLength(1, 40)
     *                 .dateRange(Instant.parse("2000-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z")))
     *         .generate();
     * }</pre>
     *
     * @param constraints the bounds to impose across the whole document
     * @see Constraints
     */
    public Gjuton withConstraints(Constraints constraints) {
        if (constraints == null) {
            throw new IllegalArgumentException("constraints must not be null");
        }
        return new Gjuton(
                schema, document, seed, producers, mode, generateAdditionalProperties, refSoftDepth, refHardDepth, constraints);
    }

    /**
     * Generates a valid JSON value for the configured schema.
     *
     * @throws UnsatisfiableSchemaException if the schema is over-constrained
     *     or the generator's random search could not find a value satisfying the
     *     schema within its retry budget
     * @throws IllegalArgumentException if a registered producer returns a value
     *     that cannot be represented as JSON
     */
    public String generate() {
        var generated = generator.generateRoot();
        return JsonSerializer.serialize(generated);
    }

    /**
     * Generates a valid JSON value and binds it to an instance of {@code type},
     * as a typed alternative to {@link #generate()}. Equivalent to deserializing
     * the JSON that {@link #generate()} would return into {@code type}.
     *
     * @throws UnsatisfiableSchemaException if the schema is over-constrained
     *     or the generator's random search could not find a value satisfying the
     *     schema within its retry budget
     * @throws JsonBindingException if the generated value does not map onto
     *     {@code type}
     */
    public <T> T generate(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        var generated = generator.generateRoot();
        return JsonSerializer.convert(generated, type);
    }

    /**
     * Generates a valid JSON value and writes it to {@code out} in UTF-8 encoding.
     * The stream is not closed.
     */
    public void generate(OutputStream out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("out must not be null");
        }
        out.write(generate().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a valid JSON value and writes it to {@code out}.
     * The writer is not closed.
     */
    public void generate(Writer out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("out must not be null");
        }
        out.write(generate());
    }

    /**
     * Generates a valid JSON value and writes it to {@code out} in UTF-8 encoding.
     * The file is created if it does not exist and truncated if it does.
     */
    public void generate(File out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("out must not be null");
        }
        Files.writeString(out.toPath(), generate());
    }

    /**
     * Returns how thoroughly {@link #generate()} has exercised this schema's
     * <em>deliberate value set</em>. Only meaningful under
     * {@link GenerationMode#EXHAUSTIVE} mode; under the default
     * {@link GenerationMode#RANDOM} mode, which emits no deliberate values,
     * this throws {@link IllegalStateException}.
     *
     * <p>The value is a fraction in {@code [0, 1]} of the deliberate value
     * set: every enum literal, each boundary value, both booleans, and each
     * const value. It is value-weighted, never decreases across calls, and is
     * exactly {@code 1.0} only once every deliberate value has been emitted.
     * This makes it safe to generate towards a target:
     *
     * <pre>{@code
     * Gjuton gen = Gjuton.of(schema).withGenerationMode(GenerationMode.EXHAUSTIVE);
     * while (gen.valueCoverage() < 0.95) {
     *     gen.generate();
     * }
     * }</pre>
     *
     * @throws IllegalStateException if this instance is in
     *     {@link GenerationMode#RANDOM} mode
     */
    public double valueCoverage() {
        if (mode == GenerationMode.RANDOM) {
            throw new IllegalStateException(
                    "valueCoverage() is only meaningful in EXHAUSTIVE mode; this instance is in RANDOM mode");
        }
        return generator.valueCoverage();
    }
}
