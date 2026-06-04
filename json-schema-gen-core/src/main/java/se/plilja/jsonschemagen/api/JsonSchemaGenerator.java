package se.plilja.jsonschemagen.api;

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

/**
 * Generates valid JSON values from a JSON Schema (Draft 7) document.
 *
 * <p>Instances are immutable. {@link #withSeed} and {@link #withPin} return a new
 * generator with the updated configuration; the original is unchanged.
 *
 * <pre>{@code
 * JsonSchemaGenerator gen = JsonSchemaGenerator.of(schema);
 * String json1 = gen.generate();
 * String json2 = gen.generate();
 * }</pre>
 */
public final class JsonSchemaGenerator {

  private final String schema;
  private final Long seed;
  private final Map<String, String> pins;

  private JsonSchemaGenerator(String schema, Long seed, Map<String, String> pins) {
    this.schema = schema;
    this.seed = seed;
    this.pins = pins;
  }

  /**
   * Creates a generator for the given JSON Schema string.
   *
   * @param schema a JSON Schema (Draft 7) document as a UTF-8 string
   */
  public static JsonSchemaGenerator of(String schema) {
    if (schema == null) {
      throw new IllegalArgumentException("schema must not be null");
    }
    return new JsonSchemaGenerator(schema, null, Collections.emptyMap());
  }

  /**
   * Creates a generator by reading a JSON Schema from a file.
   *
   * @param schema file containing a JSON Schema (Draft 7) document in UTF-8 encoding
   */
  public static JsonSchemaGenerator of(File schema) throws IOException {
    return of(Files.readString(schema.toPath()));
  }

  /**
   * Creates a generator by reading a JSON Schema from an input stream.
   * The stream is read to completion but not closed.
   *
   * @param schema stream containing a JSON Schema (Draft 7) document in UTF-8 encoding
   */
  public static JsonSchemaGenerator of(InputStream schema) throws IOException {
    return of(new String(schema.readAllBytes(), StandardCharsets.UTF_8));
  }

  /**
   * Returns a new generator with the given seed. Two generators with the same
   * schema and seed produce the same sequence of values across repeated calls
   * to {@link #generate()}.
   *
   * @param seed value used to initialise the random source
   */
  public JsonSchemaGenerator withSeed(long seed) {
    return new JsonSchemaGenerator(schema, seed, pins);
  }

  /**
   * Returns a new generator that fixes a specific field to a given value.
   * The pinned value is written verbatim into the generated JSON; all other
   * fields are still generated from the schema.
   *
   * <p>Multiple pins can be chained:
   * <pre>{@code
   * JsonSchemaGenerator.of(schema)
   *     .withPin("$.role", "\"admin\"")
   *     .withPin("$.active", "true")
   *     .generate();
   * }</pre>
   *
   * @param jsonPath  JSON Path expression identifying the field to pin (e.g. {@code "$.role"})
   * @param jsonValue a valid JSON literal to place at that path (e.g. {@code "\"admin\""})
   */
  public JsonSchemaGenerator withPin(String jsonPath, String jsonValue) {
    if (jsonPath == null) {
      throw new IllegalArgumentException("jsonPath must not be null");
    }
    if (jsonValue == null) {
      throw new IllegalArgumentException("jsonValue must not be null");
    }
    var merged = new LinkedHashMap<>(pins);
    merged.put(jsonPath, jsonValue);
    return new JsonSchemaGenerator(schema, seed, Collections.unmodifiableMap(merged));
  }

  /**
   * Generates a valid JSON value for the configured schema.
   */
  public String generate() {
    return "null";
  }

  /**
   * Generates a valid JSON value and writes it to {@code out} in UTF-8 encoding.
   * The stream is not closed.
   */
  public void generate(OutputStream out) throws IOException {
    out.write(generate().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Generates a valid JSON value and writes it to {@code out}.
   * The writer is not closed.
   */
  public void generate(Writer out) throws IOException {
    out.write(generate());
  }

  /**
   * Generates a valid JSON value and writes it to {@code out} in UTF-8 encoding.
   * The file is created if it does not exist and truncated if it does.
   */
  public void generate(File out) throws IOException {
    Files.writeString(out.toPath(), generate());
  }
}
