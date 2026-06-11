package se.plilja.jsonschemagen.internal.generator;

import java.util.Random;

public final class StringUtil {

    // TODO consider generating more tricky characters such as newlines <, > and so on
    public static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";

    private StringUtil() {
    }

    public static String randomStringOfLength(int length, Random random) {
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
