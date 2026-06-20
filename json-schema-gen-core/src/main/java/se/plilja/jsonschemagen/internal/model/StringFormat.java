package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The recognised values of the JSON Schema {@code format} keyword for strings.
 * Unrecognised format values deserialize to {@link #UNKNOWN} and are treated
 * as plain strings by the generator.
 */
public enum StringFormat {
    DATE("date"),
    DATE_TIME("date-time"),
    TIME("time"),
    EMAIL("email"),
    IDN_EMAIL("idn-email"),
    URI("uri"),
    URI_REFERENCE("uri-reference"),
    IRI("iri"),
    IRI_REFERENCE("iri-reference"),
    HOSTNAME("hostname"),
    IDN_HOSTNAME("idn-hostname"),
    IPV4("ipv4"),
    IPV6("ipv6"),
    UUID("uuid"),
    REGEX("regex"),
    JSON_POINTER("json-pointer"),
    RELATIVE_JSON_POINTER("relative-json-pointer"),
    UNKNOWN(null);

    private final String value;

    StringFormat(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static StringFormat fromValue(String value) {
        for (var format : values()) {
            if (format.value != null && format.value.equals(value)) {
                return format;
            }
        }
        return UNKNOWN;
    }
}
