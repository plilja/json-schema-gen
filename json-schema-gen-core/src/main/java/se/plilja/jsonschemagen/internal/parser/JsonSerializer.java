package se.plilja.jsonschemagen.internal.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonSerializer {

  // TODO Consider making the object mapper configurable since it will impact the generated output
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static String serialize(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize generated value", e);
    }
  }
}
