package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.internal.model.StringSchema;

class StringGeneratorTest {

  @Test
  void firstCallProducesEmptyString() {
    var generator = new StringGenerator(new Random(42));

    // when
    String result = generator.generate(new StringSchema());

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void subsequentCallsProduceNonEmptyStrings() {
    var generator = new StringGenerator(new Random(42));
    var schema = new StringSchema();
    generator.generate(schema);

    // when
    String second = generator.generate(schema);
    String third = generator.generate(schema);

    // then
    assertThat(second).isNotEmpty();
    assertThat(third).isNotEmpty();
  }
}
