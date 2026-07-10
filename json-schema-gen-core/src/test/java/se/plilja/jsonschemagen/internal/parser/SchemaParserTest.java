package se.plilja.jsonschemagen.internal.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.plilja.jsonschemagen.api.JsonSchemaGenerator;
import se.plilja.jsonschemagen.internal.model.ArraySchema;
import se.plilja.jsonschemagen.internal.model.NullSchema;
import se.plilja.jsonschemagen.internal.model.NumericSchema;
import se.plilja.jsonschemagen.internal.model.ObjectSchema;
import se.plilja.jsonschemagen.internal.model.StringFormat;
import se.plilja.jsonschemagen.internal.model.StringSchema;
import se.plilja.jsonschemagen.internal.model.UnsatisfiableSchema;
import se.plilja.jsonschemagen.internal.model.UntypedSchema;

class SchemaParserTest {

    @Nested
    class RefResolution {

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

    @Nested
    class ExternalRefs {

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
        void refsWithinExternalSchemaAreResolved(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("defs.json"), """
                    {
                        "definitions": {
                            "Order": {
                                "type": "object",
                                "properties": {
                                    "id": {"type": "integer"},
                                    "address": {"$ref": "#/definitions/Address"}
                                },
                                "required": ["id", "address"]
                            },
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
                    """);
            var schemaFile = Files.writeString(tempDir.resolve("main.json"), """
                    {
                        "type": "object",
                        "properties": {
                            "order": {"$ref": "defs.json#/definitions/Order"}
                        },
                        "required": ["order"]
                    }
                    """);

            // when
            var document = SchemaParser.parse(schemaFile);

            // then
            var order = document.resolveRef("defs.json#/definitions/Order");
            assertThat(order).isNotNull().isInstanceOf(ObjectSchema.class);
            var addressRef = document.resolveRef("defs.json#/definitions/Address");
            assertThat(addressRef).isNotNull().isInstanceOf(ObjectSchema.class);
        }

        @Test
        void generationWorksWithRefsWithinExternalSchema(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("defs.json"), """
                    {
                        "definitions": {
                            "Order": {
                                "type": "object",
                                "properties": {
                                    "id": {"type": "integer"},
                                    "address": {"$ref": "#/definitions/Address"}
                                },
                                "required": ["id", "address"]
                            },
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
                    """);
            var schemaFile = Files.writeString(tempDir.resolve("main.json"), """
                    {
                        "type": "object",
                        "properties": {
                            "order": {"$ref": "defs.json#/definitions/Order"}
                        },
                        "required": ["order"]
                    }
                    """);

            // when
            var gen = JsonSchemaGenerator.of(schemaFile.toFile()).withSeed(42);
            var json = gen.generate();

            // then
            var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            assertThat(tree.has("order")).isTrue();
            assertThat(tree.get("order").has("id")).isTrue();
            assertThat(tree.get("order").has("address")).isTrue();
            assertThat(tree.get("order").get("address").has("street")).isTrue();
            assertThat(tree.get("order").get("address").has("city")).isTrue();
        }

        @Test
        void transitiveRefsWithinExternalSchemaAreResolved(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("defs.json"), """
                    {
                        "definitions": {
                            "Order": {
                                "type": "object",
                                "properties": {
                                    "address": {"$ref": "#/definitions/Address"}
                                },
                                "required": ["address"]
                            },
                            "Address": {
                                "type": "object",
                                "properties": {
                                    "zip": {"$ref": "#/definitions/ZipCode"}
                                },
                                "required": ["zip"]
                            },
                            "ZipCode": {
                                "type": "string",
                                "minLength": 5,
                                "maxLength": 10
                            }
                        }
                    }
                    """);
            var schemaFile = Files.writeString(tempDir.resolve("main.json"), """
                    {
                        "type": "object",
                        "properties": {
                            "order": {"$ref": "defs.json#/definitions/Order"}
                        },
                        "required": ["order"]
                    }
                    """);

            // when
            var gen = JsonSchemaGenerator.of(schemaFile.toFile()).withSeed(42);
            var json = gen.generate();

            // then
            var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            var zip = tree.get("order").get("address").get("zip");
            assertThat(zip.isTextual()).isTrue();
            assertThat(zip.asText().length()).isBetween(5, 10);
        }

        @Test
        void overlappingDefinitionNamesInMainAndExternalDocDoNotCollide(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("defs.json"), """
                    {
                        "definitions": {
                            "Thing": {
                                "type": "object",
                                "properties": {
                                    "name": {"$ref": "#/definitions/Name"}
                                },
                                "required": ["name"]
                            },
                            "Name": {
                                "type": "string",
                                "minLength": 10,
                                "maxLength": 20
                            }
                        }
                    }
                    """);
            var schemaFile = Files.writeString(tempDir.resolve("main.json"), """
                    {
                        "type": "object",
                        "properties": {
                            "thing": {"$ref": "defs.json#/definitions/Thing"},
                            "localName": {"$ref": "#/definitions/Name"}
                        },
                        "definitions": {
                            "Name": {
                                "type": "string",
                                "minLength": 1,
                                "maxLength": 3
                            }
                        },
                        "required": ["thing", "localName"]
                    }
                    """);

            // when
            var gen = JsonSchemaGenerator.of(schemaFile.toFile()).withSeed(42);
            var json = gen.generate();

            // then
            var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            var externalName = tree.get("thing").get("name").asText();
            var localName = tree.get("localName").asText();
            assertThat(externalName.length()).isBetween(10, 20);
            assertThat(localName.length()).isBetween(1, 3);
        }

        @Test
        void externalSchemaReferencingAnotherExternalSchema(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("address.json"), """
                    {
                        "type": "object",
                        "properties": {
                            "street": {"type": "string"},
                            "zip": {"$ref": "zipcode.json"}
                        },
                        "required": ["street", "zip"]
                    }
                    """);
            Files.writeString(tempDir.resolve("zipcode.json"), """
                    {
                        "type": "string",
                        "minLength": 5,
                        "maxLength": 10
                    }
                    """);
            var schemaFile = Files.writeString(tempDir.resolve("main.json"), """
                    {
                        "type": "object",
                        "properties": {
                            "address": {"$ref": "address.json"}
                        },
                        "required": ["address"]
                    }
                    """);

            // when
            var gen = JsonSchemaGenerator.of(schemaFile.toFile()).withSeed(42);
            var json = gen.generate();

            // then
            var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            assertThat(tree.get("address").get("street").isTextual()).isTrue();
            var zip = tree.get("address").get("zip").asText();
            assertThat(zip.length()).isBetween(5, 10);
        }
    }

    @Nested
    class TypeArrayRewriting {

        @Test
        void typeArrayIsRewrittenToOneOf() {
            // when
            var document = SchemaParser.parse("""
                    {"type": ["string", "null"]}
                    """);

            // then
            var root = document.getRoot();
            assertThat(root).isInstanceOf(UntypedSchema.class);
            assertThat(root.getOneOf()).hasSize(1);
            assertThat(root.getOneOf().getFirst()).hasSize(2);
            assertThat(root.getOneOf().getFirst().get(0)).isInstanceOf(StringSchema.class);
            assertThat(root.getOneOf().getFirst().get(1)).isInstanceOf(NullSchema.class);
        }

        @Test
        void typeArrayPreservesConstraintsOnRelevantBranch() {
            // when
            var document = SchemaParser.parse("""
                    {"type": ["integer", "string"], "minLength": 3}
                    """);

            // then
            var root = document.getRoot();
            assertThat(root.getOneOf()).hasSize(1);
            assertThat(root.getOneOf().getFirst()).hasSize(2);
            assertThat(root.getOneOf().getFirst().get(0)).isInstanceOf(NumericSchema.class);
            var stringBranch = (StringSchema) root.getOneOf().getFirst().get(1);
            assertThat(stringBranch.getMinLength()).isEqualTo(3);
        }

    }

    @Nested
    class TypeParsing {

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
        void numberTypeParsesAsNumericSchema() {
            // when
            var document = SchemaParser.parse("""
                    {"type": "number"}
                    """);

            // then
            assertThat(document.getRoot()).isInstanceOf(NumericSchema.class);
            var schema = (NumericSchema) document.getRoot();
            assertThat(schema.getType()).isEqualTo("number");
        }

        @Test
        void integerTypePreservesTypeField() {
            // when
            var document = SchemaParser.parse("""
                    {"type": "integer"}
                    """);

            // then
            var schema = (NumericSchema) document.getRoot();
            assertThat(schema.getType()).isEqualTo("integer");
        }

        @Test
        void numberTypeParsesFractionalConstraints() {
            // when
            var document = SchemaParser.parse("""
                    {"type": "number", "minimum": 1.5, "maximum": 10.5}
                    """);

            // then
            var schema = (NumericSchema) document.getRoot();
            assertThat(schema.getMinimum()).isEqualByComparingTo(new BigDecimal("1.5"));
            assertThat(schema.getMaximum()).isEqualByComparingTo(new BigDecimal("10.5"));
        }
    }

    @Nested
    class Dependencies {

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
    }

    @Nested
    class Conditional {

        @Test
        void ifThenElseSubSchemasAreParsedOntoTheBaseSchema() {
            // when
            var document = SchemaParser.parse("""
                    {
                        "type": "object",
                        "properties": {
                            "status": {"type": "string"}
                        },
                        "if": {
                            "properties": {"status": {"const": "ok"}}
                        },
                        "then": {
                            "required": ["data"]
                        },
                        "else": {
                            "required": ["error"]
                        }
                    }
                    """);

            // then
            var root = document.getRoot();
            assertThat(root.getIfSchema()).isInstanceOf(ObjectSchema.class);
            assertThat(root.getThenSchema()).isInstanceOf(ObjectSchema.class);
            assertThat(root.getElseSchema()).isInstanceOf(ObjectSchema.class);
            assertThat(((ObjectSchema) root.getThenSchema()).getRequired()).containsExactly("data");
            assertThat(((ObjectSchema) root.getElseSchema()).getRequired()).containsExactly("error");
        }

        @Test
        void booleanSchemaFormsAreParsedForThenAndElse() {
            // when
            var document = SchemaParser.parse("""
                    {
                        "type": "object",
                        "if": {"properties": {"status": {"const": "ok"}}},
                        "then": false,
                        "else": true
                    }
                    """);

            // then
            var root = document.getRoot();
            assertThat(root.getThenSchema()).isInstanceOf(UnsatisfiableSchema.class);
            assertThat(root.getElseSchema()).isInstanceOf(UntypedSchema.class);
        }

        @Test
        void conditionalKeywordsSurviveOnAnUntypedParent() {
            // A parent carrying only if/then/else has no type-implying keyword,
            // so it stays untyped — the conditional fields must still be present.

            // when
            var document = SchemaParser.parse("""
                    {
                        "if": {"properties": {"kind": {"const": "a"}}},
                        "then": {"required": ["x"]},
                        "else": {"required": ["y"]}
                    }
                    """);

            // then
            var root = document.getRoot();
            assertThat(root).isInstanceOf(UntypedSchema.class);
            assertThat(root.getIfSchema()).isNotNull();
            assertThat(root.getThenSchema()).isNotNull();
            assertThat(root.getElseSchema()).isNotNull();
        }
    }

    @Nested
    class TypeInference {

        @Test
        void untypedSchemaWithPropertiesIsInferredAsObject() {
            // when
            var document = SchemaParser.parse("""
                    {
                        "properties": {
                            "name": {"type": "string"}
                        }
                    }
                    """);

            // then
            assertThat(document.getRoot()).isInstanceOf(ObjectSchema.class);
            var schema = (ObjectSchema) document.getRoot();
            assertThat(schema.getProperties()).containsKey("name");
        }

        @Test
        void untypedSchemaWithPatternIsInferredAsString() {
            // when
            var document = SchemaParser.parse("""
                    {"pattern": "^abc$"}
                    """);

            // then
            assertThat(document.getRoot()).isInstanceOf(StringSchema.class);
            var schema = (StringSchema) document.getRoot();
            assertThat(schema.getPattern()).isEqualTo("^abc$");
        }

        @Test
        void untypedSchemaWithMinimumIsInferredAsNumber() {
            // when
            var document = SchemaParser.parse("""
                    {"minimum": 5}
                    """);

            // then
            assertThat(document.getRoot()).isInstanceOf(NumericSchema.class);
            var schema = (NumericSchema) document.getRoot();
            assertThat(schema.getMinimum()).isEqualByComparingTo(new BigDecimal("5"));
        }

        @Test
        void untypedSchemaWithItemsIsInferredAsArray() {
            // when
            var document = SchemaParser.parse("""
                    {"items": {"type": "string"}}
                    """);

            // then
            assertThat(document.getRoot()).isInstanceOf(ArraySchema.class);
        }

        @Test
        void schemaWithKeywordsFromMultipleTypesStaysUntyped() {
            // when
            var document = SchemaParser.parse("""
                    {"pattern": "^abc$", "minimum": 5}
                    """);

            // then
            assertThat(document.getRoot()).isInstanceOf(UntypedSchema.class);
        }

        @Test
        void untypedSchemaNestedInsidePropertiesIsInferred() {
            // when
            var document = SchemaParser.parse("""
                    {
                        "type": "object",
                        "properties": {
                            "file": {"pattern": "\\\\.css$"}
                        }
                    }
                    """);

            // then
            var root = (ObjectSchema) document.getRoot();
            var file = root.getProperties().get("file");
            assertThat(file).isInstanceOf(StringSchema.class);
            assertThat(((StringSchema) file).getPattern()).isEqualTo("\\.css$");
        }

        @Test
        void constPayloadResemblingASchemaIsNotWalkedForTypeInference() {
            // when
            var document = SchemaParser.parse("""
                    {
                        "const": {"pattern": "^abc$"}
                    }
                    """);

            // then
            assertThat(document.getRoot().getConstValue())
                    .isEqualTo(java.util.Map.of("pattern", "^abc$"));
        }

        @Test
        void untypedSchemaNestedInsideOneOfBranchIsInferred() {
            // when
            var document = SchemaParser.parse("""
                    {
                        "oneOf": [
                            {"properties": {"file": {"pattern": "\\\\.css$"}}}
                        ]
                    }
                    """);

            // then
            var branch = (ObjectSchema) document.getRoot().getOneOf().getFirst().get(0);
            var file = branch.getProperties().get("file");
            assertThat(file).isInstanceOf(StringSchema.class);
            assertThat(((StringSchema) file).getPattern()).isEqualTo("\\.css$");
        }

        @Test
        void schemaWithNoTypeImplyingKeywordsStaysUntyped() {
            // when
            var document = SchemaParser.parse("""
                    {
                        "enum": ["a", "b"]
                    }
                    """);

            // then
            assertThat(document.getRoot()).isInstanceOf(UntypedSchema.class);
        }
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
