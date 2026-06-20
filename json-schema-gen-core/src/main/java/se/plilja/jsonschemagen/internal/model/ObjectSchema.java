package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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

    @Builder.Default
    private Map<String, Schema> properties = Map.of();

    @Builder.Default
    private List<String> required = List.of();

    private Integer minProperties;

    /** Either {@link Boolean} ({@code true}/{@code false}) or a {@link Schema} constraining additional property values. */
    @JsonDeserialize(using = AdditionalPropertiesDeserializer.class)
    private Object additionalProperties;

    @JsonIgnore
    public List<String> getRequiredFields() {
        return properties.keySet().stream()
                .filter(required::contains)
                .toList();
    }

    @JsonIgnore
    public List<String> getOptionalFields() {
        return properties.keySet().stream()
                .filter(name -> !required.contains(name))
                .toList();
    }
}
