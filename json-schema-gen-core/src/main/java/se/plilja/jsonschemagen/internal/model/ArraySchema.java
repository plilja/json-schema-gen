package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ArraySchema extends Schema {

    /**
     * Schema that each element of the array must satisfy.
     * Corresponds to the JSON Schema {@code items} keyword.
     */
    @JsonDeserialize(using = SchemaDeserializer.class)
    private Schema items;

    /**
     * Schema that at least one element of the array must satisfy.
     * Corresponds to the JSON Schema {@code contains} keyword.
     */
    @JsonDeserialize(using = SchemaDeserializer.class)
    private Schema contains;

    private Integer minItems;
    private Integer maxItems;
}
