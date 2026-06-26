package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ObjectSchema extends Schema {

    @JsonDeserialize(contentUsing = SchemaDeserializer.class)
    @Builder.Default
    private Map<String, Schema> properties = Map.of();

    @Builder.Default
    private List<String> required = List.of();

    private Integer minProperties;

    /**
     * Either {@link Boolean} ({@code true}/{@code false}) or a
     * {@link Schema} constraining additional property values.
     */
    @JsonDeserialize(using = BooleanOrSchemaDeserializer.class)
    private Object additionalProperties;

    /**
     * Draft 7 {@code dependencies} keyword. Each entry value is either
     * a {@code List<String>} (keys-form) or a {@link Schema} (schema-form).
     */
    @Getter(lombok.AccessLevel.NONE)
    @JsonDeserialize(using = DependenciesDeserializer.class)
    @Builder.Default
    private Map<String, Object> dependencies = Map.of();

    /**
     * Draft 2019-09+ property names that must co-occur with a given key.
     */
    @JsonProperty
    @Getter(lombok.AccessLevel.NONE)
    @Builder.Default
    private Map<String, List<String>> dependentRequired = Map.of();

    /**
     * Draft 2019-09+ sub-schemas that apply when a given key is present.
     */
    @JsonProperty
    @JsonDeserialize(contentUsing = SchemaDeserializer.class)
    @Getter(lombok.AccessLevel.NONE)
    @Builder.Default
    private Map<String, Schema> dependentSchemas = Map.of();

    /**
     * Property names that must co-occur with a given key.
     *
     * <p>Merges Draft 7 keys-form {@code dependencies} entries with
     * Draft 2019-09+ {@code dependentRequired} entries.
     */
    @JsonIgnore
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> getDependentRequired() {
        var result = new HashMap<>(dependentRequired);
        for (var entry : dependencies.entrySet()) {
            if (entry.getValue() instanceof List<?> list) {
                result.putIfAbsent(entry.getKey(), (List<String>) list);
            }
        }
        return result;
    }

    /**
     * Sub-schemas that apply when a given key is present.
     *
     * <p>Merges Draft 7 schema-form {@code dependencies} entries with
     * Draft 2019-09+ {@code dependentSchemas} entries.
     */
    @JsonIgnore
    public Map<String, Schema> getDependentSchemas() {
        var result = new HashMap<>(dependentSchemas);
        for (var entry : dependencies.entrySet()) {
            if (entry.getValue() instanceof Schema s) {
                result.putIfAbsent(entry.getKey(), s);
            }
        }
        return result;
    }

}
