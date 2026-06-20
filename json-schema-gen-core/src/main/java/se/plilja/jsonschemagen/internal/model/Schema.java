package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Base class of the JSON Schema model hierarchy.
 *
 * <p>Each concrete subclass represents one JSON Schema {@code type} keyword
 * value (string, integer, boolean, null, object, array) and carries the
 * type-specific constraint fields. Cross-cutting keywords that can appear
 * on any schema ({@code const}, {@code enum}, {@code $ref}, and the
 * combining keywords {@code oneOf}/{@code anyOf}/{@code allOf}) are fields
 * on this base class.
 */
@Getter
@EqualsAndHashCode
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

    @JsonProperty("const")
    private Object constValue;

    @JsonProperty("enum")
    private List<Object> enumValues;

    @JsonProperty("$ref")
    private String ref;

    private List<Schema> oneOf;

    private List<Schema> anyOf;

    private List<Schema> allOf;

    public abstract SchemaBuilder<?, ?> toBuilder();
}
