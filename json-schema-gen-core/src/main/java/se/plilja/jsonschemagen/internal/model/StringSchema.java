package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class StringSchema extends Schema {

    private Integer minLength;
    private Integer maxLength;
    private String pattern;

    @Override
    public Schema copyTypeSpecific() {
        return StringSchema.of(minLength, maxLength, pattern);
    }
}
