package se.plilja.jsonschemagen.internal.generator;

import static se.plilja.jsonschemagen.internal.generator.GenerationResult.result;

import java.util.LinkedHashMap;
import java.util.Map;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;

final class ObjectGenerator extends PhaseGenerator<ObjectGenerator.GenerationPhase, Map<String, Object>> {

    private final ObjectSchema schema;

    enum GenerationPhase {
        REQUIRED_ONLY, ALL_FIELDS, RANDOM
    }

    ObjectGenerator(GeneratorContext context, ObjectSchema schema) {
        super(GenerationPhase.class, context);
        this.schema = schema;
    }

    @Override
    protected GenerationPhase minimalPhase() {
        return GenerationPhase.REQUIRED_ONLY;
    }

    @Override
    protected GenerationResult<Map<String, Object>> generatePhase(GenerationPhase phase) {
        var obj = withRequiredFields();
        switch (phase) {
            case REQUIRED_ONLY -> { }
            case ALL_FIELDS -> {
                for (var field : schema.getOptionalFields()) {
                    var fieldSchema = schema.getProperties().get(field);
                    obj.put(field, context.generatorFor(fieldSchema).generate());
                }
            }
            case RANDOM -> {
                for (var field : schema.getOptionalFields()) {
                    if (context.random().nextBoolean()) {
                        var fieldSchema = schema.getProperties().get(field);
                        obj.put(field, context.generatorFor(fieldSchema).generate());
                    }
                }
            }
            default -> throw new IllegalStateException("Unhandled phase: " + phase);
        }
        padToMinProperties(obj);
        return result(obj);
    }

    private LinkedHashMap<String, Object> withRequiredFields() {
        var obj = new LinkedHashMap<String, Object>();
        // TODO validate that required fields exist in properties for a better error message
        for (var field : schema.getRequiredFields()) {
            var fieldSchema = schema.getProperties().get(field);
            obj.put(field, context.generatorFor(fieldSchema).generate());
        }
        return obj;
    }

    private void padToMinProperties(LinkedHashMap<String, Object> obj) {
        if (schema.getMinProperties() == null) {
            return;
        }
        for (var field : schema.getOptionalFields()) {
            if (obj.size() >= schema.getMinProperties()) {
                break;
            }
            if (!obj.containsKey(field)) {
                var fieldSchema = schema.getProperties().get(field);
                obj.put(field, context.generatorFor(fieldSchema).generate());
            }
        }
    }

}
