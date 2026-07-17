package io.github.gjuton.internal.generator.format;

import java.util.List;

/**
 * A character set paired with TLDs from the same writing system.
 */
record Alphabet(String chars, List<String> tlds) {

    String firstChar() {
        return chars.substring(0, 1);
    }
}
