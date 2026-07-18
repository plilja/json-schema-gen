package io.github.gjuton.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Fallback for schemas with no {@code type} field (e.g. {@code {"enum": ["a","b"]}}).
 * Used as {@code defaultImpl} in Jackson's type dispatch on {@link Schema}.
 */
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class UntypedSchema extends Schema {
}
