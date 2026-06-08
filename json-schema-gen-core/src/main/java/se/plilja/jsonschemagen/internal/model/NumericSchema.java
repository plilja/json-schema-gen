package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NumericSchema extends Schema {

    private Long minimum;
    private Long maximum;
    private Long exclusiveMinimum;
    private Long exclusiveMaximum;
    private Long multipleOf;

    @Override
    public Schema copyTypeSpecific() {
        return NumericSchema.of(minimum, maximum, exclusiveMinimum, exclusiveMaximum, multipleOf);
    }
}
