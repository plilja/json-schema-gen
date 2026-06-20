package se.plilja.jsonschemagen.internal.generator.format;

import java.util.List;

/**
 * Predefined {@link Alphabet} instances used by the format generators.
 */
final class Alphabets {

    static final Alphabet EN = new Alphabet(
            "abcdefghijklmnopqrstuvwxyz",
            List.of("com", "org", "net", "co", "se", "ai", "io", "dev",
                    "app", "info", "uk", "us", "de", "jp", "fr"));

    /**
     * Alphabet used for the SHORT/LONG canonical phases of IDN formats.
     */
    static final Alphabet IDN_CANONICAL = new Alphabet("用户例子", List.of("中国"));

    /**
     * Alphabets the IDN format generators pick from during the RANDOM phase.
     */
    static final List<Alphabet> IDN_POOL = List.of(
            IDN_CANONICAL,
            new Alphabet("ಬಲಡಟಮ", List.of("ಭಾರತ")),
            new Alphabet("अजयडट", List.of("भारत")),
            new Alphabet("квіточкапошта", List.of("укр")),
            new Alphabet("χρήστηςπαράδειγμα", List.of("ελ")),
            new Alphabet("коляпример", List.of("рф")),
            EN);

    private Alphabets() {
    }
}
