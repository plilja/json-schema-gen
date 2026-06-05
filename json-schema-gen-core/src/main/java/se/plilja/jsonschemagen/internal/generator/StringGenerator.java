package se.plilja.jsonschemagen.internal.generator;

import java.util.Random;
import se.plilja.jsonschemagen.internal.model.StringSchema;

final class StringGenerator {

  // TODO consider generating more tricky characters such as newlines <, > and so on
  private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";

  private final Random random;
  private int callCount;

  StringGenerator(Random random) {
    this.random = random;
  }

  // schema param used by minLength/maxLength in #0010
  String generate(StringSchema schema) {
    callCount++;
    if (callCount == 1) {
      return "";
    }
    int length = random.nextInt(1, 21);
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }
}
