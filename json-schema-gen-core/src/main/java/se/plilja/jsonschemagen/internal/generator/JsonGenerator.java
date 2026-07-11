package se.plilja.jsonschemagen.internal.generator;

import java.util.Random;
import se.plilja.jsonschemagen.errors.UnsatisfiableSchemaException;
import se.plilja.jsonschemagen.internal.generator.format.DateGenerator;
import se.plilja.jsonschemagen.internal.generator.format.DateTimeGenerator;
import se.plilja.jsonschemagen.internal.generator.format.EmailGenerator;
import se.plilja.jsonschemagen.internal.generator.format.HostnameGenerator;
import se.plilja.jsonschemagen.internal.generator.format.IdnEmailGenerator;
import se.plilja.jsonschemagen.internal.generator.format.IdnHostnameGenerator;
import se.plilja.jsonschemagen.internal.generator.format.Ipv4Generator;
import se.plilja.jsonschemagen.internal.generator.format.Ipv6Generator;
import se.plilja.jsonschemagen.internal.generator.format.IriGenerator;
import se.plilja.jsonschemagen.internal.generator.format.IriReferenceGenerator;
import se.plilja.jsonschemagen.internal.generator.format.JsonPointerGenerator;
import se.plilja.jsonschemagen.internal.generator.format.RegexGenerator;
import se.plilja.jsonschemagen.internal.generator.format.RelativeJsonPointerGenerator;
import se.plilja.jsonschemagen.internal.generator.format.TimeGenerator;
import se.plilja.jsonschemagen.internal.generator.format.UriGenerator;
import se.plilja.jsonschemagen.internal.generator.format.UriReferenceGenerator;
import se.plilja.jsonschemagen.internal.generator.format.UuidGenerator;
import se.plilja.jsonschemagen.internal.model.ArraySchema;
import se.plilja.jsonschemagen.internal.model.BooleanSchema;
import se.plilja.jsonschemagen.internal.model.NullSchema;
import se.plilja.jsonschemagen.internal.model.NumericSchema;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.model.SchemaDocument;
import se.plilja.jsonschemagen.internal.model.StringSchema;
import se.plilja.jsonschemagen.internal.model.UnsatisfiableSchema;
import se.plilja.jsonschemagen.internal.model.UntypedSchema;

/**
 * Internal entry point for value generation. Selects the appropriate
 * type-specific {@link Generator} for a schema and delegates to it.
 */
public final class JsonGenerator {

    private final Generator<?> delegate;

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

    private static Generator<?> buildDelegate(Schema schema, GeneratorContext context) {
        if (schema.getRef() != null) {
            return new RefGenerator(context, schema.getRef());
        }
        if (schema.getConstValue() != null) {
            return new ConstGenerator(context, schema.getConstValue(), schema);
        }
        if (schema.getEnumValues() != null) {
            return new EnumGenerator(context, schema.getEnumValues(), schema);
        }
        if (!schema.getConditionals().isEmpty()) {
            return new IfThenElseGenerator(context, schema);
        }
        if (schema.getOneOf() != null
                || schema.getAnyOf() != null
                || schema.getAllOf() != null) {
            return new AnyOfAllOfOneOfGenerator(context, schema);
        }
        return switch (schema) {
            case StringSchema s -> buildStringDelegate(s, context);
            case NumericSchema s -> new NumericGenerator(context, s);
            case BooleanSchema ignored -> new BooleanGenerator(context);
            case NullSchema ignored -> new NullGenerator();
            case ObjectSchema s -> new ObjectGenerator(context, s);
            case ArraySchema s -> new ArrayGenerator(context, s);
            case UntypedSchema ignored -> new UntypedGenerator(context);
            case UnsatisfiableSchema ignored -> throw new UnsatisfiableSchemaException(
                    "Cannot generate a value for a false schema");
        };
    }

    private static Generator<?> buildStringDelegate(StringSchema schema, GeneratorContext context) {
        var format = schema.getFormat();
        if (format == null) {
            return new StringGenerator(context, schema);
        }
        return switch (format) {
            case EMAIL -> new EmailGenerator(context, schema);
            case IDN_EMAIL -> new IdnEmailGenerator(context, schema);
            case UUID -> new UuidGenerator(context, schema);
            case DATE -> new DateGenerator(context, schema);
            case TIME -> new TimeGenerator(context, schema);
            case DATE_TIME -> new DateTimeGenerator(context, schema);
            case HOSTNAME -> new HostnameGenerator(context, schema);
            case IDN_HOSTNAME -> new IdnHostnameGenerator(context, schema);
            case IPV4 -> new Ipv4Generator(context, schema);
            case IPV6 -> new Ipv6Generator(context, schema);
            case URI -> new UriGenerator(context, schema);
            case URI_REFERENCE -> new UriReferenceGenerator(context, schema);
            case IRI -> new IriGenerator(context, schema);
            case IRI_REFERENCE -> new IriReferenceGenerator(context, schema);
            case JSON_POINTER -> new JsonPointerGenerator(context, schema);
            case RELATIVE_JSON_POINTER -> new RelativeJsonPointerGenerator(context, schema);
            case REGEX -> new RegexGenerator(context, schema);
            default -> new StringGenerator(context, schema);
        };
    }
}
