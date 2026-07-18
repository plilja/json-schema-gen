package io.github.gjuton.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Schema for both {@code "type": "integer"} and {@code "type": "number"}.
 * The {@link #type} field distinguishes the two: integer schemas produce
 * whole-number values, number schemas produce fractional values.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NumericSchema extends Schema {

    private String type;

    private BigDecimal minimum;
    private BigDecimal maximum;
    private BigDecimal exclusiveMinimum;
    private BigDecimal exclusiveMaximum;
    private BigDecimal multipleOf;

    /**
     * Whether this schema requires integer values.
     */
    public boolean isInteger() {
        return "integer".equals(type);
    }
}
