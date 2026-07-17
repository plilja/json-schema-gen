package io.github.gjuton.internal.generator;

import io.github.gjuton.internal.model.ArraySchema;
import io.github.gjuton.internal.model.BooleanSchema;
import io.github.gjuton.internal.model.NullSchema;
import io.github.gjuton.internal.model.NumericSchema;
import io.github.gjuton.internal.model.ObjectSchema;
import io.github.gjuton.internal.model.Schema;
import io.github.gjuton.internal.model.StringFormat;
import io.github.gjuton.internal.model.StringSchema;
import io.github.gjuton.internal.model.UnsatisfiableSchema;
import io.github.gjuton.internal.model.UntypedSchema;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Checks whether a generated value actually satisfies a schema, independent
 * of how the value was produced.
 *
 * <p>{@link SchemaMerger} approximates constraints it cannot express by
 * intersecting schemas (a kept-left {@code pattern} on conflict, a chosen
 * {@code oneOf} branch that turns out to also match another branch). This
 * validator re-checks a candidate value against the original, unmerged
 * schema so the generator can detect and retry a candidate that the
 * approximation let through incorrectly.
 */
final class SchemaValidator {

    private final GeneratorContext context;

    SchemaValidator(GeneratorContext context) {
        this.context = context;
    }

    /**
     * Recursion ceiling for {@code $ref} chains. A cyclic schema (a self- or
     * mutually-referencing definition with no other constraints) would
     * otherwise resolve forever without ever consuming any of the value's
     * structure. Past this depth we can no longer prove a violation, so we
     * conservatively treat the value as satisfying the schema.
     */
    private static final int MAX_REF_DEPTH = 100;

    boolean satisfies(Object value, Schema schema) {
        return satisfies(value, schema, 0);
    }

    private boolean satisfies(Object value, Schema schema, int refDepth) {
        if (value instanceof OverriddenValue) {
            // A caller-supplied override is exempt from validation; the caller
            // owns its correctness.
            return true;
        }
        if (schema.getRef() != null) {
            if (refDepth >= MAX_REF_DEPTH) {
                return true;
            }
            return satisfies(value, context.resolveRef(schema.getRef()), refDepth + 1);
        }
        if (schema.getConstValue() != null && !valuesEqual(schema.getConstValue(), value)) {
            return false;
        }
        if (schema.getEnumValues() != null
                && schema.getEnumValues().stream().noneMatch(allowed -> valuesEqual(allowed, value))) {
            return false;
        }
        if (schema.getAllOf() != null) {
            for (var branch : schema.getAllOf()) {
                if (!satisfies(value, branch, refDepth)) {
                    return false;
                }
            }
        }
        if (schema.getAnyOf() != null) {
            for (var group : schema.getAnyOf()) {
                if (group.stream().noneMatch(branch -> satisfies(value, branch, refDepth))) {
                    return false;
                }
            }
        }
        if (schema.getOneOf() != null) {
            for (var group : schema.getOneOf()) {
                if (group.stream().filter(branch -> satisfies(value, branch, refDepth)).count() != 1) {
                    return false;
                }
            }
        }
        for (var conditional : schema.getConditionals()) {
            var branch = satisfies(value, conditional.ifSchema(), refDepth)
                    ? conditional.thenSchema() : conditional.elseSchema();
            if (branch != null && !satisfies(value, branch, refDepth)) {
                return false;
            }
        }
        if (schema.getNotSchema() != null && satisfies(value, schema.getNotSchema(), refDepth)) {
            return false;
        }
        return switch (schema) {
            case StringSchema s -> satisfiesString(value, s);
            case NumericSchema s -> satisfiesNumeric(value, s);
            case BooleanSchema ignored -> value instanceof Boolean;
            case NullSchema ignored -> value == null;
            case ObjectSchema s -> satisfiesObject(value, s);
            case ArraySchema s -> satisfiesArray(value, s);
            case UntypedSchema ignored -> true;
            case UnsatisfiableSchema ignored -> false;
        };
    }

    /**
     * {@code format} is intentionally not checked: it never causes the
     * zero-/multiple-branch failures this validator exists to catch, and
     * validating it properly means re-implementing an email/URI/IPv6/etc.
     * validator per {@link StringFormat}
     * variant.
     */
    private boolean satisfiesString(Object value, StringSchema schema) {
        if (!(value instanceof String s)) {
            return false;
        }
        if (schema.getMinLength() != null && s.length() < schema.getMinLength()) {
            return false;
        }
        if (schema.getMaxLength() != null && s.length() > schema.getMaxLength()) {
            return false;
        }
        if (schema.getPattern() != null && !Pattern.compile(schema.getPattern()).matcher(s).find()) {
            return false;
        }
        return true;
    }

    private boolean satisfiesNumeric(Object value, NumericSchema schema) {
        if (!(value instanceof Number n)) {
            return false;
        }
        var v = toBigDecimal(n);
        if (schema.isInteger() && v.stripTrailingZeros().scale() > 0) {
            return false;
        }
        if (schema.getMinimum() != null && v.compareTo(schema.getMinimum()) < 0) {
            return false;
        }
        if (schema.getMaximum() != null && v.compareTo(schema.getMaximum()) > 0) {
            return false;
        }
        if (schema.getExclusiveMinimum() != null && v.compareTo(schema.getExclusiveMinimum()) <= 0) {
            return false;
        }
        if (schema.getExclusiveMaximum() != null && v.compareTo(schema.getExclusiveMaximum()) >= 0) {
            return false;
        }
        if (schema.getMultipleOf() != null
                && v.remainder(schema.getMultipleOf()).compareTo(BigDecimal.ZERO) != 0) {
            return false;
        }
        return true;
    }

    private boolean satisfiesObject(Object value, ObjectSchema schema) {
        if (!(value instanceof Map<?, ?> map)) {
            return false;
        }
        for (var required : schema.getRequired()) {
            if (!map.containsKey(required)) {
                return false;
            }
        }
        if (schema.getMinProperties() != null && map.size() < schema.getMinProperties()) {
            return false;
        }
        if (schema.getMaxProperties() != null && map.size() > schema.getMaxProperties()) {
            return false;
        }
        for (var entry : map.entrySet()) {
            var propertySchema = schema.getProperties().get(entry.getKey());
            if (propertySchema != null) {
                if (!satisfies(entry.getValue(), propertySchema)) {
                    return false;
                }
            } else if (Boolean.FALSE.equals(schema.getAdditionalProperties())) {
                return false;
            } else if (schema.getAdditionalProperties() instanceof Schema additionalSchema
                    && !satisfies(entry.getValue(), additionalSchema)) {
                return false;
            }
        }
        for (var entry : schema.getDependentRequired().entrySet()) {
            if (map.containsKey(entry.getKey()) && !map.keySet().containsAll(entry.getValue())) {
                return false;
            }
        }
        for (var entry : schema.getDependentSchemas().entrySet()) {
            if (map.containsKey(entry.getKey()) && !satisfies(value, entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean satisfiesArray(Object value, ArraySchema schema) {
        if (!(value instanceof List<?> list)) {
            return false;
        }
        if (schema.getMinItems() != null && list.size() < schema.getMinItems()) {
            return false;
        }
        if (schema.getMaxItems() != null && list.size() > schema.getMaxItems()) {
            return false;
        }
        var prefixSchemas = schema.getPrefixSchemas();
        var itemSchema = schema.getItemSchema();
        for (int i = 0; i < list.size(); i++) {
            if (i < prefixSchemas.size()) {
                if (!satisfies(list.get(i), prefixSchemas.get(i))) {
                    return false;
                }
            } else if (!schema.areAdditionalItemsAllowed()) {
                return false;
            } else if (itemSchema != null && !satisfies(list.get(i), itemSchema)) {
                return false;
            }
        }
        if (schema.getContains() != null
                && list.stream().noneMatch(item -> satisfies(item, schema.getContains()))) {
            return false;
        }
        if (schema.isUniqueItems() && new HashSet<>(list).size() != list.size()) {
            return false;
        }
        return true;
    }

    /**
     * Equality between JSON values as JSON Schema means it: two numbers are
     * equal when numerically equal regardless of their Java representation
     * (an {@code integer} 1 and a {@code number} 1.0 are equal), and all
     * other values compare by {@link Object#equals}.
     */
    private static boolean valuesEqual(Object a, Object b) {
        if (a instanceof Number an && b instanceof Number bn) {
            return toBigDecimal(an).compareTo(toBigDecimal(bn)) == 0;
        }
        return Objects.equals(a, b);
    }

    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal bd) {
            return bd;
        }
        if (n instanceof Double || n instanceof Float) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.valueOf(n.longValue());
    }
}
