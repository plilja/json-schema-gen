package se.plilja.jsonschemagen.internal.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

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
}
