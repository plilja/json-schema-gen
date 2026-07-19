package io.github.gjuton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.SpecVersionDetector;
import com.networknt.schema.ValidationMessage;
import io.github.gjuton.api.GenerationMode;
import io.github.gjuton.api.Gjuton;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Slf4j
class IntegrationTest {
    private static final int ITERATIONS = 100;
    private static final int COVERAGE_ITERATIONS = 1000;
    private static final long DEFAULT_SEED = 42L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Generous enough to avoid false positives at ITERATIONS while keeping a
    // pathological schema from hanging the suite. A single generate() call or
    // validation should complete in milliseconds; these bound the outliers.
    private static final long GENERATION_TIMEOUT_SECONDS = 2;
    private static final long VALIDATION_TIMEOUT_SECONDS = 2;

    // Daemon threads so a runaway generation/validation we can't interrupt never
    // blocks JVM exit. Shut down in @AfterAll to avoid leaking the pool.
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        var thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<VersionFlag, JsonSchemaFactory> FACTORIES = new EnumMap<>(VersionFlag.class);

    static {
        for (var flag : VersionFlag.values()) {
            FACTORIES.put(flag, JsonSchemaFactory.getInstance(flag));
        }
    }

    @AfterAll
    static void shutdownExecutor() {
        EXECUTOR.shutdownNow();
    }

    static List<Arguments> parameters() throws IOException, URISyntaxException {
        long seed = resolveSeed();
        log.info("IntegrationTest seed: {} (override with -Dtest.seed=<long>)", seed);
        return schemaPaths().parallelStream()
                .flatMap(p -> {
                    try {
                        var content = Files.readString(p);
                        // Both generation modes must always produce schema-valid JSON.
                        return Stream.of(GenerationMode.values())
                                .flatMap(mode -> {
                                    var name = p.getFileName() + " [" + mode + "]";
                                    return generateRows(name, content, p, seed, mode).stream();
                                });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    /**
     * Produces the parameterized rows for one schema in one generation mode: one
     * row per iteration on success, or a single failure row (attributed to the
     * schema) if building the generator or any generation call throws or times out.
     */
    private static List<Arguments> generateRows(String name, String content, Path path, long seed, GenerationMode mode) {
        Gjuton gen;
        try {
            gen = callWithTimeout(() -> Gjuton.of(path.toFile()).withSeed(seed).withGenerationMode(mode), GENERATION_TIMEOUT_SECONDS);
        } catch (RuntimeException e) {
            return List.of(failureRow(name, content, path, "schema build " + e.getMessage()));
        }

        var rows = new ArrayList<Arguments>(ITERATIONS);
        for (int i = 1; i <= ITERATIONS; i++) {
            String json;
            try {
                json = callWithTimeout(gen::generate, GENERATION_TIMEOUT_SECONDS);
            } catch (RuntimeException e) {
                return List.of(failureRow(name, content, path, "generation at invocation " + i + " " + e.getMessage()));
            }
            rows.add(Arguments.of(name, content, path, i, json, null));
        }
        return rows;
    }

    private static Arguments failureRow(String name, String content, Path path, String detail) {
        return Arguments.of(name, content, path, 0, null, name + ": " + detail);
    }

    static List<Arguments> schemaFiles() throws IOException, URISyntaxException {
        return schemaPaths().stream()
                .map(p -> Arguments.of(p.getFileName().toString(), p))
                .toList();
    }

    /**
     * The {@code .json} schema files under {@code src/test/resources/schemas},
     * the fixtures every parameterized integration test runs against.
     */
    private static List<Path> schemaPaths() throws IOException, URISyntaxException {
        var schemasResource = IntegrationTest.class.getClassLoader().getResource("schemas");
        var schemasDir = Paths.get(schemasResource.toURI());
        try (Stream<Path> files = Files.list(schemasDir)) {
            return files.filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.toString().endsWith(".json"))
                    .toList();
        }
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
    void generatesValidJson(String schemaName, String schemaContent, Path schemaPath, int invocation, String json, String generationError) throws Exception {
        // when
        if (generationError != null) {
            fail(generationError);
        }
        var factory = schemaFactoryFor(schemaContent);
        Set<ValidationMessage> errors = validateOrFail(factory, schemaPath, json, schemaName, invocation);

        // then
        assertThat(errors)
                .as("%s invocation=%d", schemaName, invocation)
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaFiles")
    void reachesFullCoverageWithinIterationBudget(String schemaName, Path schemaPath) throws IOException {
        // given
        var gen = Gjuton.of(schemaPath.toFile()).withSeed(DEFAULT_SEED).withGenerationMode(GenerationMode.EXHAUSTIVE);

        // then 1 -- nothing generated yet, so no deliberate value has been emitted
        assertThat(gen.valueCoverage())
                .as("%s reports zero coverage before any generation", schemaName)
                .isZero();

        // when
        int invocation = 0;
        while (gen.valueCoverage() < 1.0 && invocation < COVERAGE_ITERATIONS) {
            gen.generate();
            invocation++;
        }

        // then 2
        assertThat(gen.valueCoverage())
                .as("%s reached full value coverage within %d iterations", schemaName, COVERAGE_ITERATIONS)
                .isEqualTo(1.0);
    }

    private static Set<ValidationMessage> validateOrFail(
            JsonSchemaFactory factory, Path schemaPath, String json, String schemaName, int invocation) {
        Callable<Set<ValidationMessage>> task = () -> factory.getSchema(schemaPath.toUri()).validate(json, InputFormat.JSON);
        try {
            return callWithTimeout(task, VALIDATION_TIMEOUT_SECONDS);
        } catch (RuntimeException e) {
            return fail("%s invocation=%d: validation %s".formatted(schemaName, invocation, e.getMessage()));
        }
    }

    private static <T> T callWithTimeout(Callable<T> task, long timeoutSeconds) {
        Future<T> future = EXECUTOR.submit(task);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("timed out after " + timeoutSeconds + "s");
        } catch (ExecutionException e) {
            throw new RuntimeException("failed: " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static JsonSchemaFactory schemaFactoryFor(String schemaContent) throws Exception {
        var tree = MAPPER.readTree(schemaContent);
        var version = SpecVersionDetector.detectOptionalVersion(tree, false)
                .orElse(VersionFlag.V7);
        return FACTORIES.get(version);
    }
}
