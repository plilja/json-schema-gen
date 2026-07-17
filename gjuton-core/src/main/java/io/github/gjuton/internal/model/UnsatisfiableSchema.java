package io.github.gjuton.internal.model;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Represents a JSON Schema {@code false} literal — an unsatisfiable schema
 * that no value can ever satisfy. Appears wherever a sub-schema position
 * contains a bare {@code false} instead of a schema object.
 */
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public final class UnsatisfiableSchema extends Schema {
}
