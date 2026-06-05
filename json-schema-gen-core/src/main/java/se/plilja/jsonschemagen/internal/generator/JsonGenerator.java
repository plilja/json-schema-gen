package se.plilja.jsonschemagen.internal.generator;

import java.util.Random;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.model.StringSchema;

public final class JsonGenerator {

  private final StringGenerator stringGenerator;

  public JsonGenerator(Long seed) {
    Random random = seed != null ? new Random(seed) : new Random();
    this.stringGenerator = new StringGenerator(random);
  }

  public Object generate(Schema schema) {
    return switch (schema) {
      case StringSchema s -> stringGenerator.generate(s);
    };
  }
}
