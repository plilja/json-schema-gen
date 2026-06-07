package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;

final class ObjectGenerator extends PhaseGenerator<ObjectGenerator.GenerationPhase, Map<String, Object>> {

    private final Random random;
    private final ObjectSchema schema;
    private final Map<String, JsonGenerator> propertyGenerators;

    enum GenerationPhase {
        REQUIRED_ONLY, ALL_FIELDS, RANDOM
    }

    ObjectGenerator(Random random, ObjectSchema schema) {
        super(GenerationPhase.class);
        this.random = random;
        this.schema = schema;

        this.propertyGenerators = new LinkedHashMap<>();
        for (var entry : schema.getProperties().entrySet()) {
            propertyGenerators.put(entry.getKey(), new JsonGenerator(entry.getValue(), random));
        }
    }

    @Override
    protected GenerationResult<Map<String, Object>> generatePhase(GenerationPhase phase) {
        var obj = new LinkedHashMap<String, Object>();

        // TODO validate that required fields exist in properties for a better error message
        for (var field : schema.getRequiredFields()) {
            obj.put(field, propertyGenerators.get(field).generate());
        }

        switch (phase) {
            case REQUIRED_ONLY -> { }
            case ALL_FIELDS -> {
                for (var field : schema.getOptionalFields()) {
                    obj.put(field, propertyGenerators.get(field).generate());
                }
            }
            case RANDOM -> {
                for (var field : schema.getOptionalFields()) {
                    if (random.nextBoolean()) {
                        obj.put(field, propertyGenerators.get(field).generate());
                    }
                }
            }
        }

        return result(obj);
    }
}
