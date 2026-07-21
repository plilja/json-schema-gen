package io.github.gjuton.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gjuton.internal.model.NullSchema;
import io.github.gjuton.internal.model.SchemaDocument;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class GeneratorContextTest {

    @Test
    void producerInvokedOncePerRunAndReusedOnRevisit() {
        // given a counting producer registered at $.x
        var invocations = new AtomicInteger();
        var context = contextWithProducer("$.x", invocations::incrementAndGet);
        context.startRun();

        // when the same path is queried twice within one run, as a retry would
        var first = overrideAt(context, ".x");
        var second = overrideAt(context, ".x");

        // then the producer ran once and both visits share its value
        assertThat(invocations.get()).isEqualTo(1);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void startRunProducesFreshValueForNextRun() {
        // given a counting producer registered at $.x
        var invocations = new AtomicInteger();
        var context = contextWithProducer("$.x", invocations::incrementAndGet);

        // when each run queries the overridden path once
        context.startRun();
        overrideAt(context, ".x");
        context.startRun();
        overrideAt(context, ".x");

        // then the producer is consulted once per run
        assertThat(invocations.get()).isEqualTo(2);
    }

    private static Object overrideAt(GeneratorContext context, String pathSegment) {
        context.enterPath(pathSegment);
        try {
            return context.currentOverride();
        } finally {
            context.exitPath(pathSegment);
        }
    }

    private static GeneratorContext contextWithProducer(String path, Supplier<Object> producer) {
        var config = new GeneratorConfig(false, false, 2, 4, Map.of(path, producer), ValueConstraints.forExhaustive());
        return new GeneratorContext(new SchemaDocument(new NullSchema(), Map.of()), new Random(1), config);
    }
}
