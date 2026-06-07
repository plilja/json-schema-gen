package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;

class ObjectGeneratorTest {

    @Test
    void generatesObjectWithRequiredStringField() {
        var schema = TestParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"}
                    },
                    "required": ["name"]
                }
                """, ObjectSchema.class);
        var generator = new ObjectGenerator(new Random(42), schema);

        // when
        var result = generator.generate();

        // then
        assertThat(result).containsKey("name");
        assertThat(result.get("name")).isInstanceOf(String.class);
    }

    @Test
    void requiredFieldsAlwaysPresent() {
        var schema = TestParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "nickname": {"type": "string"}
                    },
                    "required": ["name"]
                }
                """, ObjectSchema.class);
        var generator = new ObjectGenerator(new Random(42), schema);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).containsKey("name"));
    }

    @Test
    void optionalFieldsAppearBothPresentAndAbsent() {
        var schema = TestParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "nickname": {"type": "string"}
                    },
                    "required": ["name"]
                }
                """, ObjectSchema.class);
        var generator = new ObjectGenerator(new Random(42), schema);

        // when
        var results = IntStream.range(0, 2)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).anyMatch(obj -> obj.containsKey("nickname"));
        assertThat(results).anyMatch(obj -> !obj.containsKey("nickname"));
    }

    @Test
    void emptyPropertiesGeneratesEmptyObject() {
        var schema = TestParser.parse("""
                {
                    "type": "object",
                    "properties": {}
                }
                """, ObjectSchema.class);
        var generator = new ObjectGenerator(new Random(42), schema);

        // when
        var result = generator.generate();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void absentPropertiesAndRequiredGeneratesEmptyObject() {
        var schema = TestParser.parse("""
                {"type": "object"}
                """, ObjectSchema.class);
        var generator = new ObjectGenerator(new Random(42), schema);

        // when
        var result = generator.generate();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void nestedObjectsAreGeneratedRecursively() {
        var schema = TestParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "address": {
                            "type": "object",
                            "properties": {
                                "street": {"type": "string"}
                            },
                            "required": ["street"]
                        }
                    },
                    "required": ["address"]
                }
                """, ObjectSchema.class);
        var generator = new ObjectGenerator(new Random(42), schema);

        // when
        var result = generator.generate();

        // then
        assertThat(result).containsKey("address");
        var address = (Map<String, Object>) result.get("address");
        assertThat(address).containsKey("street");
        assertThat(address.get("street")).isInstanceOf(String.class);
    }
}
