package se.plilja.jsonschemagen.internal.generator;

import java.util.Random;
import se.plilja.jsonschemagen.internal.model.ArraySchema;
import se.plilja.jsonschemagen.internal.model.BooleanSchema;
import se.plilja.jsonschemagen.internal.model.NumericSchema;
import se.plilja.jsonschemagen.internal.model.NullSchema;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;
import se.plilja.jsonschemagen.internal.model.Schema;
import se.plilja.jsonschemagen.internal.model.StringSchema;
import se.plilja.jsonschemagen.internal.model.UntypedSchema;

// TODO consider naming and javadoc. The name is confusingly close to JsonSchemaGenerator
public final class JsonGenerator {

    private final PhaseGenerator<?, ?> delegate;

    public JsonGenerator(Long seed, Schema schema) {
        this(schema, seed != null ? new Random(seed) : new Random());
    }

    JsonGenerator(Schema schema, Random random) {
        if (schema.getEnumValues() != null) {
            this.delegate = new EnumGenerator(random, schema.getEnumValues());
        } else {
            this.delegate = switch (schema) {
                case StringSchema s -> new StringGenerator(random, s);
                case NumericSchema s -> new NumericGenerator(random, s);
                case BooleanSchema ignored -> new BooleanGenerator(random);
                case NullSchema ignored -> new NullGenerator();
                case ObjectSchema s -> new ObjectGenerator(random, s);
                case ArraySchema s -> new ArrayGenerator(random, s);
                case UntypedSchema ignored -> throw new IllegalArgumentException("Schema has no type and no enum");
            };
        }
    }

    public Object generate() {
        return delegate.generate();
    }
}
