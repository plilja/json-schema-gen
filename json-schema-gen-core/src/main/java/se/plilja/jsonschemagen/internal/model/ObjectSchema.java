package se.plilja.jsonschemagen.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ObjectSchema extends Schema {

    private Map<String, Schema> properties = Map.of();
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
