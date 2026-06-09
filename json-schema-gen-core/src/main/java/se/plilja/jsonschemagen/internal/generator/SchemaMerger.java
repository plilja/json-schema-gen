package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.FunctionalUtil.coalesce;
import static se.plilja.jsonschemagen.internal.generator.FunctionalUtil.maxNullable;
import static se.plilja.jsonschemagen.internal.generator.FunctionalUtil.minNullable;

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
import se.plilja.jsonschemagen.internal.model.UntypedSchema;

final class SchemaMerger {

    private SchemaMerger() {
    }

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
        rejectUnsupportedComposition(a, b);

        Schema merged;
        if (b instanceof UntypedSchema) {
            merged = a.toBuilder().build();
        } else if (a instanceof UntypedSchema) {
            merged = b.toBuilder().build();
        } else if (a instanceof StringSchema sa && b instanceof StringSchema sb) {
            if (sa.getPattern() != null && sb.getPattern() != null) {
                throw new UnsatisfiableSchemaException(
                        "Cannot merge branches with conflicting pattern constraints");
            }
            merged = StringSchema.builder()
                    .minLength(maxNullable(sa.getMinLength(), sb.getMinLength()))
                    .maxLength(minNullable(sa.getMaxLength(), sb.getMaxLength()))
                    .pattern(coalesce(sa.getPattern(), sb.getPattern()))
                    .build();
        } else if (a instanceof NumericSchema na && b instanceof NumericSchema nb) {
            merged = NumericSchema.builder()
                    .minimum(maxNullable(na.getMinimum(), nb.getMinimum()))
                    .maximum(minNullable(na.getMaximum(), nb.getMaximum()))
                    .exclusiveMinimum(maxNullable(na.getExclusiveMinimum(), nb.getExclusiveMinimum()))
                    .exclusiveMaximum(minNullable(na.getExclusiveMaximum(), nb.getExclusiveMaximum()))
                    .multipleOf(MathUtil.lcmNullable(na.getMultipleOf(), nb.getMultipleOf()))
                    .build();
        } else if (a instanceof ObjectSchema oa && b instanceof ObjectSchema ob) {
            var properties = new LinkedHashMap<>(oa.getProperties());
            for (var entry : ob.getProperties().entrySet()) {
                properties.merge(entry.getKey(), entry.getValue(), SchemaMerger::mergeTwoSchemas);
            }
            var required = Stream.concat(oa.getRequired().stream(), ob.getRequired().stream())
                    .distinct()
                    .toList();
            merged = ObjectSchema.builder()
                    .properties(properties)
                    .required(required)
                    .build();
        } else if (a instanceof ArraySchema aa && b instanceof ArraySchema ab) {
            Schema items;
            if (aa.getItems() != null && ab.getItems() != null) {
                items = mergeTwoSchemas(aa.getItems(), ab.getItems());
            } else {
                items = coalesce(aa.getItems(), ab.getItems());
            }
            merged = ArraySchema.builder()
                    .items(items)
                    .minItems(maxNullable(aa.getMinItems(), ab.getMinItems()))
                    .maxItems(minNullable(aa.getMaxItems(), ab.getMaxItems()))
                    .build();
        } else {
            throw new UnsatisfiableSchemaException(
                    "Cannot merge schemas of types " + a.getClass().getSimpleName()
                            + " and " + b.getClass().getSimpleName());
        }

        return merged.toBuilder()
                .enumValues(mergeEnumValues(a.getEnumValues(), b.getEnumValues()))
                .build();
    }

    private static void rejectUnsupportedComposition(Schema a, Schema b) {
        if (a.getRef() != null || b.getRef() != null) {
            throw new IllegalArgumentException(
                    "allOf composition with $ref sub-schemas is not yet supported");
        }
        if (a.getOneOf() != null || b.getOneOf() != null) {
            throw new IllegalArgumentException(
                    "allOf composition with nested oneOf is not yet supported");
        }
        if (a.getAllOf() != null || b.getAllOf() != null) {
            throw new IllegalArgumentException(
                    "nested allOf inside allOf is not yet supported");
        }
    }

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
