package se.plilja.jsonschemagen.internal.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import se.plilja.jsonschemagen.internal.model.Schema;

public final class SchemaParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static Schema parse(String jsonSchema) {
    try {
      return MAPPER.readValue(jsonSchema, Schema.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to parse JSON Schema", e);
    }
  }
}
