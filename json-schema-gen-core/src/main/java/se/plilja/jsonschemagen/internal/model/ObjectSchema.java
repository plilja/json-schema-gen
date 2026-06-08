package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ObjectSchema extends Schema {

    @Builder.Default
    private Map<String, Schema> properties = Map.of();

    @Builder.Default
    private List<String> required = List.of();

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
