package se.plilja.jsonschemagen.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;
import se.plilja.jsonschemagen.internal.parser.SchemaParser;

class ObjectGeneratorTest {

    @Test
    void generatesObjectWithRequiredStringField() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"}
                    },
                    "required": ["name"]
                }
                """);

        // when
        var result = generator.generate();

        // then
        assertThat(result).containsKey("name");
        assertThat(result.get("name")).isInstanceOf(String.class);
    }

    @Test
    void requiredFieldsAlwaysPresent() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "nickname": {"type": "string"}
                    },
                    "required": ["name"]
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).containsKey("name"));
    }

    @Test
    void optionalFieldsAppearBothPresentAndAbsent() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "nickname": {"type": "string"}
                    },
                    "required": ["name"]
                }
                """);

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
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {}
                }
                """);

        // when
        var result = generator.generate();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void absentPropertiesAndRequiredGeneratesEmptyObject() {
        var generator = objectGenerator("""
                {"type": "object"}
                """);

        // when
        var result = generator.generate();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void nestedObjectsAreGeneratedRecursively() {
        var generator = objectGenerator("""
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
                """);

        // when
        var result = generator.generate();

        // then
        assertThat(result).containsKey("address");
        var address = (Map<String, Object>) result.get("address");
        assertThat(address).containsKey("street");
        assertThat(address.get("street")).isInstanceOf(String.class);
    }

    @Test
    void additionalPropertiesFalseStillEmitsDeclaredOptionalFields() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "nickname": {"type": "string"}
                    },
                    "required": ["name"],
                    "additionalProperties": false
                }
                """);

        // when
        var results = IntStream.range(0, 2)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).anyMatch(obj -> obj.containsKey("nickname"));
    }

    @Test
    void minPropertiesWithRequiredFieldsCountsRequiredTowardMinimum() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "id": {"type": "integer"},
                        "name": {"type": "string"},
                        "nickname": {"type": "string"}
                    },
                    "required": ["id"],
                    "minProperties": 2
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> {
            assertThat(obj).hasSizeGreaterThanOrEqualTo(2);
            assertThat(obj).containsKey("id");
        });
    }

    @Test
    void minPropertiesForcesOptionalFieldsToAppear() {
        var generator = objectGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "string"},
                        "b": {"type": "string"},
                        "c": {"type": "string"}
                    },
                    "minProperties": 2
                }
                """);

        // when
        var results = IntStream.range(0, 20)
                .mapToObj(i -> generator.generate())
                .toList();

        // then
        assertThat(results).allSatisfy(obj -> assertThat(obj).hasSizeGreaterThanOrEqualTo(2));
    }

    private static ObjectGenerator objectGenerator(String json) {
        var document = SchemaParser.parse(json);
        return new ObjectGenerator(new GeneratorContext(document, new Random(42)), (ObjectSchema) document.getRoot());
    }
}
