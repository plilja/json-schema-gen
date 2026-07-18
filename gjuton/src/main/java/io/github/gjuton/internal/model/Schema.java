package io.github.gjuton.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
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
 * on any schema are fields on this base class.
 */
@Getter
@EqualsAndHashCode
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = UntypedSchema.class, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StringSchema.class, name = "string"),
        @JsonSubTypes.Type(value = NumericSchema.class, name = "integer"),
        @JsonSubTypes.Type(value = NumericSchema.class, name = "number"),
        @JsonSubTypes.Type(value = BooleanSchema.class, name = "boolean"),
        @JsonSubTypes.Type(value = NullSchema.class, name = "null"),
        @JsonSubTypes.Type(value = ObjectSchema.class, name = "object"),
        @JsonSubTypes.Type(value = ArraySchema.class, name = "array"),
})
public abstract sealed class Schema
        permits StringSchema, NumericSchema, BooleanSchema, NullSchema, ObjectSchema, ArraySchema,
        UntypedSchema, UnsatisfiableSchema {

    @JsonProperty("const")
    private Object constValue;

    @JsonProperty("enum")
    private List<Object> enumValues;

    @JsonProperty("$ref")
    private String ref;

    private List<List<Schema>> oneOf;

    private List<List<Schema>> anyOf;

    @JsonDeserialize(contentUsing = SchemaDeserializer.class)
    private List<Schema> allOf;

    @JsonProperty("if")
    @JsonDeserialize(using = SchemaDeserializer.class)
    private Schema ifSchema;

    @JsonProperty("then")
    @JsonDeserialize(using = SchemaDeserializer.class)
    private Schema thenSchema;

    @JsonProperty("else")
    @JsonDeserialize(using = SchemaDeserializer.class)
    private Schema elseSchema;

    @JsonProperty("not")
    @JsonDeserialize(using = SchemaDeserializer.class)
    private Schema notSchema;

    /**
     * Conditionals accumulated from merging with other schemas that each
     * declared their own {@code if}/{@code then}/{@code else}; not a JSON
     * Schema keyword, so never populated by parsing. See {@link #getConditionals()}.
     */
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    private List<Conditional> additionalConditionals;

    @JsonSetter("oneOf")
    @JsonDeserialize(contentUsing = SchemaDeserializer.class)
    private void setOneOf(List<Schema> oneOf) {
        this.oneOf = oneOf == null ? null : List.of(List.copyOf(oneOf));
    }

    @JsonSetter("anyOf")
    @JsonDeserialize(contentUsing = SchemaDeserializer.class)
    private void setAnyOf(List<Schema> anyOf) {
        this.anyOf = anyOf == null ? null : List.of(List.copyOf(anyOf));
    }

    public abstract SchemaBuilder<?, ?> toBuilder();

    /**
     * Every {@code if}/{@code then}/{@code else} conditional this schema
     * enforces: its own (when declared directly), plus any accumulated by
     * merging with other schemas that each declared one. All must hold
     * independently, the same as {@code allOf} branches.
     */
    public List<Conditional> getConditionals() {
        var result = new ArrayList<Conditional>();
        if (ifSchema != null) {
            result.add(new Conditional(ifSchema, thenSchema, elseSchema));
        }
        if (additionalConditionals != null) {
            result.addAll(additionalConditionals);
        }
        return result;
    }

    /**
     * One {@code if}/{@code then}/{@code else} triple pulled off a schema.
     */
    public record Conditional(Schema ifSchema, Schema thenSchema, Schema elseSchema) {
    }
}
