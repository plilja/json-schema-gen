package io.github.gjuton.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gjuton.errors.UnsatisfiableSchemaException;
import io.github.gjuton.internal.parser.SchemaParser;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RefGeneratorTest {

    @Test
    @SuppressWarnings("unchecked")
    void selfRefResolvesToRootSchema() {
        var generator = refGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "value": {"type": "string"},
                        "children": {
                            "type": "array",
                            "items": {"$ref": "#"}
                        }
                    },
                    "required": ["value"]
                }
                """, "#");

        // when
        var result = (Map<String, Object>) generator.generate();

        // then
        assertThat(result).containsKey("value");
    }

    @Test
    @SuppressWarnings("unchecked")
    void refToDefinitionResolvesToTargetSchema() {
        var generator = refGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "billing": {"$ref": "#/definitions/Address"}
                    },
                    "required": ["billing"],
                    "definitions": {
                        "Address": {
                            "type": "object",
                            "properties": {
                                "street": {"type": "string"},
                                "city": {"type": "string"}
                            },
                            "required": ["street", "city"]
                        }
                    }
                }
                """, "#/definitions/Address");

        // when
        var result = (Map<String, Object>) generator.generate();

        // then
        assertThat(result).containsKeys("street", "city");
    }

    @Test
    void requiredSelfRefThrowsUnsatisfiableSchemaException() {
        var generator = refGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "self": {"$ref": "#"}
                    },
                    "required": ["self"]
                }
                """, "#");

        // when / then
        assertThatThrownBy(generator::generate)
                .isInstanceOf(UnsatisfiableSchemaException.class)
                .hasMessageContaining("#")
                .hasMessageContaining("infinite recursion");
    }

    @Test
    void recursiveSchemaWithOptionalSelfRefTerminatesAcrossManyIterations() {
        // children is optional (no minItems) — the soft limit propagates minimal
        // mode into the array, which collapses to length 0, breaking the cycle.
        var generator = refGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "value": {"type": "string"},
                        "children": {
                            "type": "array",
                            "items": {"$ref": "#"}
                        }
                    },
                    "required": ["value"]
                }
                """, "#");

        // when / then
        IntStream.range(0, 1000).forEach(i -> {
            var result = generator.generate();
            assertThat(result).isInstanceOf(Map.class);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void recursionDepthIsBounded() {
        // Optional children — recursion is reachable via ALL_FIELDS phase but not
        // forced. The soft limit must keep observed depth bounded even when the
        // optional path is exercised.
        var generator = refGenerator("""
                {
                    "type": "object",
                    "properties": {
                        "value": {"type": "string"},
                        "children": {
                            "type": "array",
                            "items": {"$ref": "#"}
                        }
                    },
                    "required": ["value"]
                }
                """, "#");

        // when
        var maxDepth = IntStream.range(0, 1000)
                .map(i -> treeDepth((Map<String, Object>) generator.generate()))
                .max()
                .orElseThrow();

        // then
        // GLOBAL_HARD_DEPTH is 4 ref expansions; observable JSON nesting can be
        // a small multiple of that (object → array → object → ...). Anything
        // well below a stack-overflow ceiling is fine.
        assertThat(maxDepth).isLessThan(50);
    }

    @SuppressWarnings("unchecked")
    private static int treeDepth(Map<String, Object> node) {
        var children = (java.util.List<Object>) node.get("children");
        if (children == null || children.isEmpty()) {
            return 1;
        }
        int max = 0;
        for (var child : children) {
            int d = treeDepth((Map<String, Object>) child);
            if (d > max) {
                max = d;
            }
        }
        return 1 + max;
    }

    @Test
    void manySelfReferencingPropertiesCompleteInBoundedTime() {
        // Schema mimics Renovate-style: many properties all pointing $ref: "#".
        // Without a global depth limit this causes exponential blowup.
        var props = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            if (i > 0) {
                props.append(",");
            }
            props.append("\"prop").append(i).append("\": {\"$ref\": \"#\"}");
        }
        var schema = """
                {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        %s
                    },
                    "required": ["name"],
                    "additionalProperties": false
                }
                """.formatted(props);
        var document = SchemaParser.parse(schema);
        var gen = new JsonGenerator(42L, document, GeneratorConfig.defaults());

        // when
        // second call hits MAX_PROPERTIES phase, expanding all 50 refs
        gen.generate();
        var result = gen.generate();

        // then
        assertThat(result).isInstanceOf(Map.class);
    }

    private static RefGenerator refGenerator(String json, String ref) {
        var document = SchemaParser.parse(json);
        return new RefGenerator(new GeneratorContext(document, new Random(42)), ref);
    }
}
