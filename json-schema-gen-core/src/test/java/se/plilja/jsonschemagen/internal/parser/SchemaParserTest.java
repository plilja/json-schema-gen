package se.plilja.jsonschemagen.internal.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
    void httpRefResolvesViaNetwork() throws IOException {
        var server = startSchemaServer("/schema.json", """
                {"type": "string", "minLength": 1}
                """);
        try {
            int port = server.getAddress().getPort();

            // when
            var document = SchemaParser.parse("""
                    {
                        "type": "object",
                        "properties": {
                            "name": {"$ref": "http://localhost:%d/schema.json"}
                        }
                    }
                    """.formatted(port));

            // then
            var resolved = document.resolveRef("http://localhost:%d/schema.json".formatted(port));
            assertThat(resolved).isNotNull().isInstanceOf(StringSchema.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpRefWithFragmentResolvesDefinition() throws IOException {
        var server = startSchemaServer("/defs.json", """
                {
                    "definitions": {
                        "Tag": {"type": "string", "maxLength": 50}
                    }
                }
                """);
        try {
            int port = server.getAddress().getPort();

            // when
            var document = SchemaParser.parse("""
                    {
                        "type": "object",
                        "properties": {
                            "tag": {"$ref": "http://localhost:%d/defs.json#/definitions/Tag"}
                        }
                    }
                    """.formatted(port));

            // then
            var resolved = document.resolveRef("http://localhost:%d/defs.json#/definitions/Tag".formatted(port));
            assertThat(resolved).isNotNull().isInstanceOf(StringSchema.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void relativeRefWithNoBaseDirThrowsIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> SchemaParser.parse("""
                {
                    "type": "object",
                    "properties": {
                        "external": {"$ref": "other-schema.json#/definitions/Foo"}
                    }
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("other-schema.json")
                .hasMessageContaining("no base URI");
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
    void fileRefResolvesToExternalSchema() throws URISyntaxException {
        var schemaFile = testResourcePath("schemas/ref-external-file.json");

        // when
        var document = SchemaParser.parse(schemaFile);

        // then
        var resolved = document.resolveRef("external/defs.json#/definitions/Address");
        assertThat(resolved).isNotNull();
        assertThat(resolved).isInstanceOf(ObjectSchema.class);
    }

    @Test
    void twoFragmentsIntoSameExternalFileBothResolve(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("defs.json"), """
                {
                    "definitions": {
                        "Address": {
                            "type": "object",
                            "properties": {"street": {"type": "string"}},
                            "required": ["street"]
                        },
                        "ZipCode": {
                            "type": "string",
                            "minLength": 5
                        }
                    }
                }
                """);
        var schemaFile = Files.writeString(tempDir.resolve("main.json"), """
                {
                    "type": "object",
                    "properties": {
                        "address": {"$ref": "defs.json#/definitions/Address"},
                        "zip": {"$ref": "defs.json#/definitions/ZipCode"}
                    }
                }
                """);

        // when
        var document = SchemaParser.parse(schemaFile);

        // then
        var address = document.resolveRef("defs.json#/definitions/Address");
        var zip = document.resolveRef("defs.json#/definitions/ZipCode");
        assertThat(address).isNotNull().isInstanceOf(ObjectSchema.class);
        assertThat(zip).isNotNull().isInstanceOf(StringSchema.class);
    }

    @Test
    void fileRefWithoutFragmentResolvesToExternalRoot(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("leaf.json"), """
                {"type": "string", "minLength": 1}
                """);
        var schemaFile = Files.writeString(tempDir.resolve("main.json"), """
                {
                    "type": "object",
                    "properties": {
                        "name": {"$ref": "leaf.json"}
                    }
                }
                """);

        // when
        var document = SchemaParser.parse(schemaFile);

        // then
        var resolved = document.resolveRef("leaf.json");
        assertThat(resolved).isNotNull();
        assertThat(resolved).isInstanceOf(StringSchema.class);
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

    private static Path testResourcePath(String relativePath) throws URISyntaxException {
        return Paths.get(SchemaParserTest.class.getClassLoader().getResource(relativePath).toURI());
    }

    private static HttpServer startSchemaServer(String path, String body) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            var bytes = body.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });
        server.start();
        return server;
    }
}
