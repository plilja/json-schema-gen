package se.plilja.jsonschemagen;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import se.plilja.jsonschemagen.api.JsonSchemaGenerator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationTest {

  private static final JsonSchemaFactory FACTORY =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

  static List<Arguments> parameters() throws IOException, URISyntaxException {
    Path schemasDir = Paths.get(
        IntegrationTest.class.getClassLoader().getResource("schemas").toURI());
    try (Stream<Path> files = Files.list(schemasDir)) {
      return files
          .filter(p -> p.toString().endsWith(".json"))
          .flatMap(p -> {
            String name = p.getFileName().toString();
            String content;
            try {
              content = Files.readString(p);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            return IntStream.range(1, 21)
                .mapToObj(i -> Arguments.of(name, content, i));
          })
          .toList();
    }
  }

  @ParameterizedTest(name = "{0} invocation={2}")
  @MethodSource("parameters")
  void generatesValidJson(String schemaName, String schemaContent, int invocation) {
    var gen = JsonSchemaGenerator.of(schemaContent);

    // when
    String json = null;
    for (int i = 0; i < invocation; i++) {
      json = gen.generate();
    }

    // then
    Set<ValidationMessage> errors = FACTORY.getSchema(schemaContent)
        .validate(json, InputFormat.JSON);
    assertThat(errors)
        .as("%s invocation=%d", schemaName, invocation)
        .isEmpty();
  }
}
