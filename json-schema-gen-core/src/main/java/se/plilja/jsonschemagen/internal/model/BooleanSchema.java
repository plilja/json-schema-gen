package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class BooleanSchema extends Schema {

    @Override
    public Schema copyTypeSpecific() {
        return new BooleanSchema();
    }
}
