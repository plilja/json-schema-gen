package se.plilja.jsonschemagen.internal.generator;

import java.util.Random;
import se.plilja.jsonschemagen.internal.generator.format.EmailGenerator;
import se.plilja.jsonschemagen.internal.generator.format.UuidGenerator;
import se.plilja.jsonschemagen.internal.model.ArraySchema;
import se.plilja.jsonschemagen.internal.model.BooleanSchema;
import se.plilja.jsonschemagen.internal.model.NullSchema;
import se.plilja.jsonschemagen.internal.model.NumericSchema;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.model.SchemaDocument;
import se.plilja.jsonschemagen.internal.model.StringSchema;
import se.plilja.jsonschemagen.internal.model.UntypedSchema;

// TODO consider naming and javadoc. The name is confusingly close to JsonSchemaGenerator
public final class JsonGenerator {

    private final PhaseGenerator<?, ?> delegate;

    public JsonGenerator(Long seed, SchemaDocument document) {
        this(document.getRoot(),
                new GeneratorContext(document, seed != null ? new Random(seed) : new Random()));
    }

    JsonGenerator(Schema schema, GeneratorContext context) {
        this.delegate = buildDelegate(schema, context);
    }

    public Object generate() {
        return delegate.generate();
    }

    private static PhaseGenerator<?, ?> buildDelegate(Schema schema, GeneratorContext context) {
        if (schema.getRef() != null) {
            return new RefGenerator(context, schema.getRef());
        }
        if (schema.getEnumValues() != null) {
            return new EnumGenerator(context, schema.getEnumValues());
        }
        if (schema.getOneOf() != null) {
            return new OneOfGenerator(context, schema);
        }
        if (schema.getAnyOf() != null) {
            return new AnyOfGenerator(context, schema);
        }
        if (schema.getAllOf() != null) {
            return new AllOfGenerator(context, schema);
        }
        return switch (schema) {
            case StringSchema s -> buildStringDelegate(s, context);
            case NumericSchema s -> new NumericGenerator(context, s);
            case BooleanSchema ignored -> new BooleanGenerator(context);
            case NullSchema ignored -> new NullGenerator(context);
            case ObjectSchema s -> new ObjectGenerator(context, s);
            case ArraySchema s -> new ArrayGenerator(context, s);
            case UntypedSchema ignored -> throw new IllegalArgumentException("Schema has no type and no enum");
        };
    }

    private static PhaseGenerator<?, ?> buildStringDelegate(StringSchema schema, GeneratorContext context) {
        var format = schema.getFormat();
        if (format == null) {
            return new StringGenerator(context, schema);
        }
        return switch (format) {
            case EMAIL -> new EmailGenerator(context, schema);
            case UUID -> new UuidGenerator(context, schema);
            default -> new StringGenerator(context, schema);
        };
    }
}
