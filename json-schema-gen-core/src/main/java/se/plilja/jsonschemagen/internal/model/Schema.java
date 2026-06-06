package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StringSchema.class, name = "string"),
        @JsonSubTypes.Type(value = IntegerSchema.class, name = "integer"),
        @JsonSubTypes.Type(value = BooleanSchema.class, name = "boolean"),
})
public abstract sealed class Schema permits StringSchema, IntegerSchema, BooleanSchema {
}
