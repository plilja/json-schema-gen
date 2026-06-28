package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.util.FunctionalUtil.coalesce;
import static se.plilja.jsonschemagen.internal.util.MathUtil.maxNullable;
import static se.plilja.jsonschemagen.internal.util.MathUtil.minNullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.model.ArraySchema;
import se.plilja.jsonschemagen.internal.model.NumericSchema;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.model.StringSchema;
import se.plilja.jsonschemagen.internal.model.UnsatisfiableSchema;
import se.plilja.jsonschemagen.internal.model.UntypedSchema;
import se.plilja.jsonschemagen.internal.util.MathUtil;

/**
 * Combines multiple schemas into one by taking the intersection of their
 * constraints. Used by {@code allOf} (all branches must hold) and by
 * {@code anyOf}/{@code oneOf} when parent-level sibling constraints need
 * to be folded into each branch.
 */
final class SchemaMerger {

    private SchemaMerger() {
    }

    /**
     * Merges a list of schemas pairwise from left to right, producing a
     * single schema whose constraints are the intersection of all inputs.
     *
     * @throws IllegalArgumentException      if the list is empty
     * @throws UnsatisfiableSchemaException   if any pair has incompatible types or constraints
     */
    static Schema merge(List<Schema> schemas) {
        if (schemas.isEmpty()) {
            throw new IllegalArgumentException("merge requires at least one schema");
        }
        var result = schemas.get(0);
        for (int i = 1; i < schemas.size(); i++) {
            result = mergeTwoSchemas(result, schemas.get(i));
        }
        return result;
    }

    /**
     * Merges each schema in {@code schemas} with {@code other}, returning
     * only the compatible results. Schemas that are incompatible with
     * {@code other} are silently omitted from the returned list.
     */
    static List<Schema> mergeEachWith(List<Schema> schemas, Schema other) {
        var result = new ArrayList<Schema>();
        for (var schema : schemas) {
            try {
                result.add(merge(List.of(schema, other)));
            } catch (UnsatisfiableSchemaException ignored) {
                // Schema incompatible with the other — drop it.
            }
        }
        return result;
    }

    private static Schema mergeTwoSchemas(Schema a, Schema b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        rejectUnsupportedComposition(a, b);

        Schema merged;
        if (a instanceof UnsatisfiableSchema || b instanceof UnsatisfiableSchema) {
            merged = new UnsatisfiableSchema();
        } else if (b instanceof UntypedSchema) {
            merged = a.toBuilder().build();
        } else if (a instanceof UntypedSchema) {
            merged = b.toBuilder().build();
        } else if (a instanceof StringSchema sa && b instanceof StringSchema sb) {
            merged = mergeStringSchemas(sa, sb);
        } else if (a instanceof NumericSchema na && b instanceof NumericSchema nb) {
            merged = mergeNumericSchemas(na, nb);
        } else if (a instanceof ObjectSchema oa && b instanceof ObjectSchema ob) {
            merged = mergeObjectSchemas(oa, ob);
        } else if (a instanceof ArraySchema aa && b instanceof ArraySchema ab) {
            merged = mergeArraySchemas(aa, ab);
        } else {
            throw new UnsatisfiableSchemaException(
                    "Cannot merge schemas of types " + a.getClass().getSimpleName()
                            + " and " + b.getClass().getSimpleName());
        }

        var constValue = mergeConstValues(a.getConstValue(), b.getConstValue());
        var enumValues = mergeEnumValues(a.getEnumValues(), b.getEnumValues());
        if (constValue != null && enumValues != null && !enumValues.contains(constValue)) {
            throw new UnsatisfiableSchemaException(
                    "const value " + constValue + " is not in enum " + enumValues);
        }
        return merged.toBuilder()
                .constValue(constValue)
                .enumValues(enumValues)
                .build();
    }

    private static StringSchema mergeStringSchemas(StringSchema a, StringSchema b) {
        if (a.getPattern() != null && b.getPattern() != null) {
            throw new UnsatisfiableSchemaException(
                    "Cannot merge branches with conflicting pattern constraints");
        }
        return StringSchema.builder()
                .minLength(maxNullable(a.getMinLength(), b.getMinLength()))
                .maxLength(minNullable(a.getMaxLength(), b.getMaxLength()))
                .pattern(coalesce(a.getPattern(), b.getPattern()))
                .build();
    }

    private static NumericSchema mergeNumericSchemas(NumericSchema a, NumericSchema b) {
        return NumericSchema.builder()
                .minimum(maxNullable(a.getMinimum(), b.getMinimum()))
                .maximum(minNullable(a.getMaximum(), b.getMaximum()))
                .exclusiveMinimum(maxNullable(a.getExclusiveMinimum(), b.getExclusiveMinimum()))
                .exclusiveMaximum(minNullable(a.getExclusiveMaximum(), b.getExclusiveMaximum()))
                .multipleOf(MathUtil.lcmNullable(a.getMultipleOf(), b.getMultipleOf()))
                .build();
    }

    private static ObjectSchema mergeObjectSchemas(ObjectSchema a, ObjectSchema b) {
        var properties = new LinkedHashMap<>(a.getProperties());
        for (var entry : b.getProperties().entrySet()) {
            properties.merge(entry.getKey(), entry.getValue(), SchemaMerger::mergeTwoSchemas);
        }
        var required = Stream.concat(a.getRequired().stream(), b.getRequired().stream())
                .distinct()
                .toList();
        var additionalProperties = mergeBooleanOrSchema(a.getAdditionalProperties(), b.getAdditionalProperties());
        return ObjectSchema.builder()
                .properties(properties)
                .required(required)
                .additionalProperties(additionalProperties)
                .minProperties(maxNullable(a.getMinProperties(), b.getMinProperties()))
                .maxProperties(minNullable(a.getMaxProperties(), b.getMaxProperties()))
                .build();
    }

    private static ArraySchema mergeArraySchemas(ArraySchema a, ArraySchema b) {
        var items = mergeTwoSchemas(a.getItemSchema(), b.getItemSchema());
        var contains = mergeTwoSchemas(a.getContains(), b.getContains());
        var prefixA = a.getPrefixSchemas();
        var prefixB = b.getPrefixSchemas();
        List<Schema> mergedPrefix = null;
        if (!prefixA.isEmpty() || !prefixB.isEmpty()) {
            int len = Math.max(prefixA.size(), prefixB.size());
            mergedPrefix = new ArrayList<>();
            for (int i = 0; i < len; i++) {
                var pa = i < prefixA.size() ? prefixA.get(i) : null;
                var pb = i < prefixB.size() ? prefixB.get(i) : null;
                mergedPrefix.add(mergeTwoSchemas(pa, pb));
            }
        }
        var mergedAdditionalItems = a.areAdditionalItemsAllowed() && b.areAdditionalItemsAllowed() ? null : Boolean.FALSE;
        return ArraySchema.builder()
                .items(items)
                .prefixItems(mergedPrefix)
                .additionalItems(mergedAdditionalItems)
                .contains(contains)
                .minItems(maxNullable(a.getMinItems(), b.getMinItems()))
                .maxItems(minNullable(a.getMaxItems(), b.getMaxItems()))
                .build();
    }

    private static void rejectUnsupportedComposition(Schema a, Schema b) {
        if (a.getOneOf() != null || b.getOneOf() != null) {
            throw new IllegalArgumentException(
                    "allOf composition with nested oneOf is not yet supported");
        }
        if (a.getAllOf() != null || b.getAllOf() != null) {
            throw new IllegalArgumentException(
                    "nested allOf inside allOf is not yet supported");
        }
    }

    /**
     * Merges two values that are either {@link Boolean} or {@link Schema}.
     * {@code false} wins over everything; a {@link Schema} wins over
     * {@code true} (more restrictive); two schemas are merged with
     * {@link #mergeTwoSchemas}.
     */
    private static Object mergeBooleanOrSchema(Object a, Object b) {
        if (Boolean.FALSE.equals(a) || Boolean.FALSE.equals(b)) {
            return Boolean.FALSE;
        }
        if (a instanceof Schema sa && b instanceof Schema sb) {
            return mergeTwoSchemas(sa, sb);
        }
        if (a instanceof Schema) {
            return a;
        }
        if (b instanceof Schema) {
            return b;
        }
        return coalesce(a, b);
    }

    /**
     * Merges two {@code const} values. If both are present they must be
     * equal; otherwise the schemas are unsatisfiable. A single non-null
     * value passes through unchanged.
     */
    private static Object mergeConstValues(Object a, Object b) {
        if (a == null || b == null) {
            return coalesce(a, b);
        }
        if (!a.equals(b)) {
            throw new UnsatisfiableSchemaException(
                    "const branches have conflicting values: " + a + " vs " + b);
        }
        return a;
    }

    /**
     * Merges two {@code enum} value lists by intersection. If both are
     * present the result contains only values common to both; an empty
     * intersection means the schemas are unsatisfiable. A single non-null
     * list passes through unchanged.
     */
    private static List<Object> mergeEnumValues(List<Object> a, List<Object> b) {
        if (a == null || b == null) {
            return coalesce(a, b);
        }
        var intersection = a.stream().filter(b::contains).toList();
        if (intersection.isEmpty()) {
            throw new UnsatisfiableSchemaException(
                    "enum branches have no common values");
        }
        return intersection;
    }
}
