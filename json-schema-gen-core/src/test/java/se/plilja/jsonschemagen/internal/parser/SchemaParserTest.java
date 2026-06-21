package se.plilja.jsonschemagen.internal.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import java.util.List;
import se.plilja.jsonschemagen.internal.model.NullSchema;
import se.plilja.jsonschemagen.internal.model.NumericSchema;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;
import se.plilja.jsonschemagen.internal.model.StringFormat;
import se.plilja.jsonschemagen.internal.model.StringSchema;
import se.plilja.jsonschemagen.internal.model.UntypedSchema;

class SchemaParserTest {

    @Test
    void selfRefResolvesToRootSchema() {
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "self": {"$ref": "#"}
                    }
                }
                """);

        // when
        var resolved = document.resolveRef("#");

        // then
        assertThat(resolved).isSameAs(document.getRoot());
    }

    @Test
    void refInDefinitionsIsCollected() {
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "billing": {"$ref": "#/definitions/Address"}
                    },
                    "definitions": {
                        "Address": {
                            "type": "object",
                            "properties": {"street": {"type": "string"}}
                        }
                    }
                }
                """);

        // when
        var resolved = document.resolveRef("#/definitions/Address");

        // then
        assertThat(resolved).isNotNull();
    }

    @Test
    void refIn$defsIsCollected() {
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "tag": {"$ref": "#/$defs/Tag"}
                    },
                    "$defs": {
                        "Tag": {"type": "string"}
                    }
                }
                """);

        // when
        var resolved = document.resolveRef("#/$defs/Tag");

        // then
        assertThat(resolved).isNotNull();
    }

    @Test
    void refNestedInsideArrayItemsIsCollected() {
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "tags": {
                            "type": "array",
                            "items": {"$ref": "#/definitions/Tag"}
                        }
                    },
                    "definitions": {
                        "Tag": {"type": "string"}
                    }
                }
                """);

        // when
        var resolved = document.resolveRef("#/definitions/Tag");

        // then
        assertThat(resolved).isNotNull();
    }

    @Test
    void refInsideArrayElementIsCollected() {
        // $ref lives as an element of a JSON array (oneOf), not as a property
        // value. Exercises the array-recursion branch of collectRefs.
        var document = SchemaParser.parse("""
                {
                    "oneOf": [
                        {"$ref": "#/definitions/Tag"}
                    ],
                    "definitions": {
                        "Tag": {"type": "string"}
                    }
                }
                """);

        // when
        var resolved = document.resolveRef("#/definitions/Tag");

        // then
        assertThat(resolved).isNotNull();
    }

    @Test
    void refNestedDeepInsideObjectPropertyIsCollected() {
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "outer": {
                            "type": "object",
                            "properties": {
                                "inner": {
                                    "type": "object",
                                    "properties": {
                                        "leaf": {"$ref": "#/definitions/Leaf"}
                                    }
                                }
                            }
                        }
                    },
                    "definitions": {
                        "Leaf": {"type": "string"}
                    }
                }
                """);

        // when
        var resolved = document.resolveRef("#/definitions/Leaf");

        // then
        assertThat(resolved).isNotNull();
    }

    @Test
    void multipleRefsToSameTargetStringResolveToSameSchemaInstance() {
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "billing": {"$ref": "#/definitions/Address"},
                        "shipping": {"$ref": "#/definitions/Address"}
                    },
                    "definitions": {
                        "Address": {
                            "type": "object",
                            "properties": {"street": {"type": "string"}}
                        }
                    }
                }
                """);

        // when
        var first = document.resolveRef("#/definitions/Address");
        var second = document.resolveRef("#/definitions/Address");

        // then
        assertThat(first).isSameAs(second);
    }

    @Test
    void distinctRefsAreEachCollected() {
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "a": {"$ref": "#/definitions/A"},
                        "b": {"$ref": "#/definitions/B"}
                    },
                    "definitions": {
                        "A": {"type": "string"},
                        "B": {"type": "integer"}
                    }
                }
                """);

        // when
        var a = document.resolveRef("#/definitions/A");
        var b = document.resolveRef("#/definitions/B");

        // then
        assertThat(a).isNotNull();
        assertThat(b).isNotNull();
        assertThat(a).isNotSameAs(b);
    }

    @Test
    void unresolvedRefThrowsIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "missing": {"$ref": "#/definitions/DoesNotExist"}
                    }
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("#/definitions/DoesNotExist");
    }

    @Test
    void externalUriRefThrowsIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "external": {"$ref": "http://example.com/schema.json"}
                    }
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only internal");
    }

    @Test
    void unknownFormatResolvesToUnknownSentinel() {
        var document = SchemaParser.parse("""
                {
                    "type": "string",
                    "format": "made-up"
                }
                """);

        // when
        var schema = (StringSchema) document.getRoot();

        // then
        assertThat(schema.getFormat()).isEqualTo(StringFormat.UNKNOWN);
    }

    @Test
    void unresolvedRefIsDetectedAtParseTimeNotGenerationTime() {
        // A $ref nested deep inside an unreachable branch is still walked by
        // collectRefs, so a typo surfaces at parse time even if generation
        // would never reach that branch.

        // when / then
        assertThatThrownBy(() -> SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "deeply": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "x": {"$ref": "#/definitions/Missing"}
                                }
                            }
                        }
                    }
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("#/definitions/Missing");
    }

    @Test
    void typeArrayIsRewrittenToOneOf() {
        // when
        var document = SchemaParser.parse("""
                {"type": ["string", "null"]}
                """);

        // then
        var root = document.getRoot();
        assertThat(root).isInstanceOf(UntypedSchema.class);
        assertThat(root.getOneOf()).hasSize(2);
        assertThat(root.getOneOf().get(0)).isInstanceOf(StringSchema.class);
        assertThat(root.getOneOf().get(1)).isInstanceOf(NullSchema.class);
    }

    @Test
    void dependentRequiredKeywordIsParsedDirectly() {
        // when
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "foo": { "type": "string" },
                        "bar": { "type": "integer" }
                    },
                    "dependentRequired": {
                        "foo": ["bar"]
                    }
                }
                """);

        // then
        var root = (ObjectSchema) document.getRoot();
        assertThat(root.getDependentRequired())
                .containsEntry("foo", List.of("bar"));
    }

    @Test
    void dependentSchemasKeywordIsParsedDirectly() {
        // when
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "foo": { "type": "string" }
                    },
                    "dependentSchemas": {
                        "foo": {
                            "type": "object",
                            "properties": {
                                "bar": { "type": "integer" }
                            },
                            "required": ["bar"]
                        }
                    }
                }
                """);

        // then
        var root = (ObjectSchema) document.getRoot();
        assertThat(root.getDependentSchemas()).containsKey("foo");
        var depSchema = (ObjectSchema) root.getDependentSchemas().get("foo");
        assertThat(depSchema.getRequired()).containsExactly("bar");
    }

    @Test
    void draft7DependenciesSchemaFormIsNormalisedToDependentSchemas() {
        // when
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "foo": { "type": "string" }
                    },
                    "dependencies": {
                        "foo": {
                            "properties": {
                                "bar": { "type": "integer" }
                            },
                            "required": ["bar"]
                        }
                    }
                }
                """);

        // then
        var root = (ObjectSchema) document.getRoot();
        assertThat(root.getDependentSchemas()).containsKey("foo");
        var depSchema = (ObjectSchema) root.getDependentSchemas().get("foo");
        assertThat(depSchema.getRequired()).containsExactly("bar");
    }

    @Test
    void draft7DependenciesArrayFormIsNormalisedToDependentRequired() {
        // when
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "foo": { "type": "string" },
                        "bar": { "type": "integer" }
                    },
                    "dependencies": {
                        "foo": ["bar"]
                    }
                }
                """);

        // then
        var root = (ObjectSchema) document.getRoot();
        assertThat(root.getDependentRequired())
                .containsEntry("foo", List.of("bar"));
    }

    @Test
    void draft7DependenciesMixedFormIsSplitCorrectly() {
        // when
        var document = SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "a": { "type": "string" },
                        "b": { "type": "string" },
                        "c": { "type": "string" }
                    },
                    "dependencies": {
                        "a": ["b"],
                        "b": {
                            "properties": { "c": { "type": "string" } },
                            "required": ["c"]
                        }
                    }
                }
                """);

        // then
        var root = (ObjectSchema) document.getRoot();
        assertThat(root.getDependentRequired())
                .containsEntry("a", List.of("b"));
        assertThat(root.getDependentSchemas()).containsKey("b");
        var depSchema = (ObjectSchema) root.getDependentSchemas().get("b");
        assertThat(depSchema.getRequired()).containsExactly("c");
    }

    @Test
    void typeArrayPreservesConstraintsOnRelevantBranch() {
        // when
        var document = SchemaParser.parse("""
                {"type": ["integer", "string"], "minLength": 3}
                """);

        // then
        var root = document.getRoot();
        assertThat(root.getOneOf()).hasSize(2);
        assertThat(root.getOneOf().get(0)).isInstanceOf(NumericSchema.class);
        var stringBranch = (StringSchema) root.getOneOf().get(1);
        assertThat(stringBranch.getMinLength()).isEqualTo(3);
    }
}
