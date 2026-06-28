package se.plilja.jsonschemagen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.SpecVersionDetector;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import se.plilja.jsonschemagen.api.JsonSchemaGenerator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class IntegrationTest {
    private static final int ITERATIONS = 250;
    private static final long DEFAULT_SEED = 42L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<VersionFlag, JsonSchemaFactory> FACTORIES = new EnumMap<>(VersionFlag.class);

    static {
        for (var flag : VersionFlag.values()) {
            FACTORIES.put(flag, JsonSchemaFactory.getInstance(flag));
        }
    }

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
                .filter(p -> !Files.isDirectory(p))
                .flatMap(p -> {
                    try {
                        var name = p.getFileName().toString();
                        var content = Files.readString(p);
                        var gen = JsonSchemaGenerator.of(p.toFile()).withSeed(seed);
                        var args = new ArrayList<Arguments>(ITERATIONS);
                        for (int i = 1; i <= ITERATIONS; i++) {
                            args.add(Arguments.of(name, content, p, i, gen.generate()));
                        }
                        return args.stream();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
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

    @ParameterizedTest(name = "{0} invocation={3}")
    @MethodSource("parameters")
    void generatesValidJson(String schemaName, String schemaContent, Path schemaPath, int invocation, String json) throws Exception {
        // when
        var factory = schemaFactoryFor(schemaContent);
        Set<ValidationMessage> errors = factory.getSchema(schemaPath.toUri())
                .validate(json, InputFormat.JSON);

        // then
        assertThat(errors)
                .as("%s invocation=%d", schemaName, invocation)
                .isEmpty();
    }

    private static JsonSchemaFactory schemaFactoryFor(String schemaContent) throws Exception {
        var tree = MAPPER.readTree(schemaContent);
        var version = SpecVersionDetector.detectOptionalVersion(tree, false)
                .orElse(VersionFlag.V7);
        return FACTORIES.get(version);
    }
}
