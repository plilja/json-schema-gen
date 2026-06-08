package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = UntypedSchema.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StringSchema.class, name = "string"),
        @JsonSubTypes.Type(value = NumericSchema.class, name = "integer"),
        @JsonSubTypes.Type(value = BooleanSchema.class, name = "boolean"),
        @JsonSubTypes.Type(value = NullSchema.class, name = "null"),
        @JsonSubTypes.Type(value = ObjectSchema.class, name = "object"),
        @JsonSubTypes.Type(value = ArraySchema.class, name = "array"),
})
public abstract sealed class Schema permits StringSchema, NumericSchema, BooleanSchema, NullSchema, ObjectSchema, ArraySchema, UntypedSchema {

    @JsonProperty("enum")
    private List<Object> enumValues;

    @JsonProperty("$ref")
    private String ref;

    private List<Schema> oneOf;

    private List<Schema> allOf;

    public abstract SchemaBuilder<?, ?> toBuilder();
}
