package io.github.gjuton.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gjuton.internal.model.BooleanSchema;
import org.junit.jupiter.api.Test;

class JsonGeneratorNoveltyTest {

    @Test
    void generateRootCompletesTheRunSoNoveltyScoreReflectsIt() {
        var context = TestContexts.withSeed(1);
        var generator = new JsonGenerator(new BooleanSchema(), context);

        // when
        generator.generateRoot();

        // then
        assertThat(context.noveltyScore()).isEqualTo(1.0);
    }

    @Test
    void noveltyScoreDelegatesToTheSharedContext() {
        var context = TestContexts.withSeed(1);
        var generator = new JsonGenerator(new BooleanSchema(), context);

        // when
        generator.generateRoot();

        // then
        assertThat(generator.noveltyScore()).isEqualTo(context.noveltyScore());
    }
}
