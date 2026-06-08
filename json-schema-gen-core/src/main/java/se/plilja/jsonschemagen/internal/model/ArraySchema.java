package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ArraySchema extends Schema {

    private Schema items;
    private Integer minItems;
    private Integer maxItems;

    @Override
    public Schema copyTypeSpecific() {
        return ArraySchema.of(items, minItems, maxItems);
    }
}
