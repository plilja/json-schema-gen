package se.plilja.jsonschemagen;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.plilja.jsonschemagen.api.JsonSchemaGenerator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class IntegrationTest {
    private static final int ITERATIONS = 250;
    private static final long DEFAULT_SEED = 42L;

    private static final JsonSchemaFactory JSON_SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    static List<Arguments> parameters() throws IOException, URISyntaxException {
        long seed = resolveSeed();
        log.info("IntegrationTest seed: {} (override with -Dtest.seed=<long>)", seed);
        Path schemasDir = Paths.get(
                IntegrationTest.class.getClassLoader().getResource("schemas").toURI());
        List<Path> schemaFiles;
        try (Stream<Path> files = Files.list(schemasDir)) {
            schemaFiles = files.filter(p -> p.toString().endsWith(".json")).toList();
        }
        return schemaFiles.parallelStream()
                .flatMap(p -> {
                    String name = p.getFileName().toString();
                    String content;
                    try {
                        content = Files.readString(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    var gen = JsonSchemaGenerator.of(content).withSeed(seed);
                    var args = new ArrayList<Arguments>(ITERATIONS);
                    for (int i = 1; i <= ITERATIONS; i++) {
                        args.add(Arguments.of(name, content, i, gen.generate()));
                    }
                    return args.stream();
                })
                .toList();
    }

    private static long resolveSeed() {
        String value = System.getProperty("test.seed");
        if (value == null || value.equals("random")) {
            return DEFAULT_SEED;
        }
        return Long.parseLong(value);
    }

    @ParameterizedTest(name = "{0} invocation={2}")
    @MethodSource("parameters")
    void generatesValidJson(String schemaName, String schemaContent, int invocation, String json) {
        // when
        Set<ValidationMessage> errors = JSON_SCHEMA_FACTORY.getSchema(schemaContent)
                .validate(json, InputFormat.JSON);

        // then
        assertThat(errors)
                .as("%s invocation=%d", schemaName, invocation)
                .isEmpty();
    }
}
