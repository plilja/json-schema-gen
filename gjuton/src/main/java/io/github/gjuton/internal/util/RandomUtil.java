package io.github.gjuton.internal.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class RandomUtil {

    // TODO consider generating more tricky characters such as newlines <, > and so on
    public static final String ENGLISH_ALPHABET = "abcdefghijklmnopqrstuvwxyz";

    private RandomUtil() {
    }

    public static String randomStringOfLength(int length, Random random) {
        return randomStringOfLength(ENGLISH_ALPHABET, length, random);
    }

    /**
     * A random string of {@code length} characters, each independently chosen
     * from {@code alphabet}.
     */
    public static String randomStringOfLength(String alphabet, int length, Random random) {
        var codePoints = alphabet.codePoints().toArray();
        var sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.appendCodePoint(codePoints[random.nextInt(codePoints.length)]);
        }
        return sb.toString();
    }

    public static <T> T randomOne(List<T> items, Random random) {
        return items.get(random.nextInt(items.size()));
    }

    public static <T> List<T> randomSubset(List<T> items, int n, Random random) {
        var shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, n);
    }
}
