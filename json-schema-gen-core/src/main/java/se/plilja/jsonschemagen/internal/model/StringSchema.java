package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class StringSchema extends Schema {

    private Integer minLength;
    private Integer maxLength;
    private String pattern;
    private StringFormat format;
}
