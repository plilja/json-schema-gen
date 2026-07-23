package io.github.gjuton.internal.generator;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gjuton.internal.model.NullSchema;
import io.github.gjuton.internal.model.Schema;
import io.github.gjuton.internal.model.SchemaDocument;
import io.github.gjuton.internal.parser.SchemaParser;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GeneratorContextTest {

    @Nested
    class NoveltyTracking {

        @Test
        void scoreIsOneBeforeAnyCompletedCall() {
            var context = TestContexts.withSeed(1);

            // then
            assertThat(context.noveltyScore()).isEqualTo(1.0);
        }

        @Test
        void scoreIsOneAfterARunThatRegisteredANovelVisit() {
            var context = TestContexts.withSeed(1);
            var generator = fakeGenerator();

            // when
            context.registerVisit(generator, 0);
            context.completeRun();

            // then
            assertThat(context.noveltyScore()).isEqualTo(1.0);
        }

        @Test
        void scoreReflectsNonNovelRunsInWindow() {
            var context = TestContexts.withSeed(1);
            var generator = fakeGenerator();

            // when
            context.startRun();
            context.registerVisit(generator, 0);
            context.completeRun();

            context.startRun();
            context.registerVisit(generator, 0);
            context.completeRun();

            // then
            assertThat(context.noveltyScore()).isEqualTo(0.5);
        }

        @Test
        void windowKeepsOnlyTheFiveMostRecentRuns() {
            var context = TestContexts.withSeed(1);
            var generator = fakeGenerator();

            // when
            // six runs: the first five each visit a fresh index (novel), the
            // sixth revisits an index already seen (not novel) — if the window
            // were unbounded the oldest novel run would still count
            for (int index = 0; index < 5; index++) {
                context.startRun();
                context.registerVisit(generator, index);
                context.completeRun();
            }
            context.startRun();
            context.registerVisit(generator, 4);
            context.completeRun();

            // then
            assertThat(context.noveltyScore()).isEqualTo(0.8);
        }

        @Test
        void differentGeneratorsTrackNoveltyIndependently() {
            var context = TestContexts.withSeed(1);
            var first = fakeGenerator();
            var second = fakeGenerator();

            // when
            context.startRun();
            context.registerVisit(first, 0);
            context.completeRun();

            context.startRun();
            context.registerVisit(second, 0);
            context.completeRun();

            // then
            assertThat(context.noveltyScore()).isEqualTo(1.0);
        }

        @Test
        void rollbackUndoesARegisteredVisitAndTheNoveltyItCaused() {
            var context = TestContexts.withSeed(1);
            var generator = fakeGenerator();

            // when
            // first run registers a visit, then discards it via rollback — the
            // discarded visit must neither mark this run as novel nor prevent
            // the same index from being novel again in a later run
            context.startRun();
            var mark = context.checkpoint();
            context.registerVisit(generator, 0);
            context.rollback(mark);
            context.completeRun();

            context.startRun();
            context.registerVisit(generator, 0);
            context.completeRun();

            // then
            assertThat(context.noveltyScore()).isEqualTo(0.5);
        }

        @Test
        void rollbackLeavesVisitsRegisteredBeforeTheMarkIntact() {
            var context = TestContexts.withSeed(1);
            var first = fakeGenerator();
            var second = fakeGenerator();

            // when
            context.startRun();
            context.registerVisit(first, 0);
            var mark = context.checkpoint();
            context.registerVisit(second, 0);
            context.rollback(mark);
            context.completeRun();

            // first's visit predates the mark and must survive rollback
            context.startRun();
            context.registerVisit(first, 0);
            context.completeRun();

            // second's visit was rolled back, so it is novel again
            context.startRun();
            context.registerVisit(second, 0);
            context.completeRun();

            // then
            assertThat(context.noveltyScore()).isEqualTo(2.0 / 3.0);
        }

        @Test
        void rollbackAfterRevisitingTheSameIndexLeavesTheEarlierVisitIntact() {
            var context = TestContexts.withSeed(1);
            var generator = fakeGenerator();

            // when
            // the same generator/index is visited once before the checkpoint and
            // once after — rolling back the second visit must not erase the first
            context.startRun();
            context.registerVisit(generator, 0);
            var mark = context.checkpoint();
            context.registerVisit(generator, 0);
            context.rollback(mark);
            context.completeRun();

            // the index was already seen in the first run, so revisiting it here is not novel
            context.startRun();
            context.registerVisit(generator, 0);
            context.completeRun();

            // then
            assertThat(context.noveltyScore()).isEqualTo(0.5);
        }

        @Test
        void startRunResetsTheVisitJournalSoCheckpointStartsFresh() {
            var context = TestContexts.withSeed(1);
            var generator = fakeGenerator();

            // when
            context.startRun();
            context.registerVisit(generator, 0);
            context.completeRun();

            context.startRun();

            // then
            assertThat(context.checkpoint()).isEqualTo(0);
        }
    }

    @Nested
    class PerGeneratorNoveltyTracking {

        @Test
        void scoreIsEmptyBeforeTheGeneratorHasEverBeenVisited() {
            var context = TestContexts.withSeed(1);

            // then
            assertThat(context.noveltyScore(fakeGenerator())).isEmpty();
        }

        @Test
        void scoreIsOneAfterARunThatRegisteredANovelVisit() {
            var context = TestContexts.withSeed(1);
            var generator = fakeGenerator();

            // when
            context.startRun();
            context.registerVisit(generator, 0);
            context.completeRun();

            // then
            assertThat(context.noveltyScore(generator)).contains(1.0);
        }

        @Test
        void scoreReflectsNonNovelRunsInWindow() {
            var context = TestContexts.withSeed(1);
            var generator = fakeGenerator();

            // when
            context.startRun();
            context.registerVisit(generator, 0);
            context.completeRun();

            context.startRun();
            context.registerVisit(generator, 0);
            context.completeRun();

            // then
            assertThat(context.noveltyScore(generator)).contains(0.5);
        }

        @Test
        void windowKeepsOnlyTheFiveMostRecentRunsForTheGenerator() {
            var context = TestContexts.withSeed(1);
            var generator = fakeGenerator();

            // when
            for (int index = 0; index < 5; index++) {
                context.startRun();
                context.registerVisit(generator, index);
                context.completeRun();
            }
            context.startRun();
            context.registerVisit(generator, 4);
            context.completeRun();

            // then
            assertThat(context.noveltyScore(generator)).contains(0.8);
        }

        @Test
        void aRunThatDoesNotVisitTheGeneratorLeavesItsScoreUnaffected() {
            var context = TestContexts.withSeed(1);
            var first = fakeGenerator();
            var second = fakeGenerator();

            // when
            context.startRun();
            context.registerVisit(first, 0);
            context.completeRun();

            // then
            assertThat(context.noveltyScore(second)).isEmpty();
        }

        @Test
        void multipleVisitsToTheSameGeneratorWithinOneRunCountAsOneWindowEntry() {
            var context = TestContexts.withSeed(1);
            var generator = fakeGenerator();

            // when
            // one run visits the generator at two distinct (both novel) indices -
            // still one completed run, so it must contribute exactly one window
            // entry, not two
            context.startRun();
            context.registerVisit(generator, 0);
            context.registerVisit(generator, 1);
            context.completeRun();

            context.startRun();
            context.registerVisit(generator, 0);
            context.completeRun();

            // then
            // two window entries (one per run): the first run was novel, the
            // second revisited an already-seen index and was not
            assertThat(context.noveltyScore(generator)).contains(0.5);
        }

        @Test
        void rollbackExcludesTheDiscardedVisitFromTheGeneratorsNovelty() {
            var context = TestContexts.withSeed(1);
            var generator = fakeGenerator();

            // when
            context.startRun();
            var mark = context.checkpoint();
            context.registerVisit(generator, 0);
            context.rollback(mark);
            context.completeRun();

            // then
            assertThat(context.noveltyScore(generator)).isEmpty();
        }
    }

    // A fresh instance every call - a captureless lambda would be interned by
    // the JVM into a singleton, defeating tests that rely on distinct
    // generator identity (novelty is tracked per generator instance).
    private static Generator<Object> fakeGenerator() {
        return new Generator<>() {
            @Override
            public Object generate() {
                return null;
            }
        };
    }

    @Nested
    class MergedSchemaCaching {

        @Test
        void singleElementListBypassesTheCacheAndReturnsThatElement() {
            var context = TestContexts.withSeed(1);
            var schema = new NullSchema();

            // when
            var result = context.mergedSchema(List.of(schema));

            // then
            assertThat(result).isSameAs(schema);
        }

        @Test
        void equalMultiElementListsReturnTheSameMergedInstance() {
            var context = TestContexts.withSeed(1);

            // when
            var first = context.mergedSchema(List.of(parse("""
                    {"type": "string", "minLength": 2}
                    """), parse("""
                    {"maxLength": 10}
                    """)));
            var second = context.mergedSchema(List.of(parse("""
                    {"type": "string", "minLength": 2}
                    """), parse("""
                    {"maxLength": 10}
                    """)));

            // then
            assertThat(second).isSameAs(first);
        }

        @Test
        void differentOrderingsOfTheSameSchemasReturnTheSameMergedInstance() {
            var context = TestContexts.withSeed(1);
            var a = parse("{\"type\": \"string\", \"minLength\": 2}");
            var b = parse("{\"maxLength\": 10}");

            // when
            var first = context.mergedSchema(List.of(a, b));
            var second = context.mergedSchema(List.of(b, a));

            // then
            assertThat(second).isSameAs(first);
        }

        @Test
        void evictingFromTheCacheAlsoDropsTheCorrespondingGenerator() {
            var context = TestContexts.withSeed(1);
            var schemaA = context.mergedSchema(List.of(parse("""
                    {"type": "string", "minLength": 2}
                    """), parse("""
                    {"maxLength": 10}
                    """)));
            var generatorBefore = context.generatorFor(schemaA);

            // when
            // fill the cache past capacity with distinct merges so schemaA's entry is evicted
            for (int i = 0; i < GeneratorContext.MERGED_SCHEMA_CACHE_CAPACITY; i++) {
                context.mergedSchema(List.of(
                        parse("{\"minLength\": " + i + "}"),
                        parse("{\"maxLength\": 999}")));
            }

            var generatorAfter = context.generatorFor(schemaA);

            // then
            assertThat(generatorAfter).isNotSameAs(generatorBefore);
        }

        private static Schema parse(String json) {
            return SchemaParser.parse(json).getRoot();
        }
    }

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
        var config = new GeneratorConfig(false, false, 2, 4, Map.of(path, producer), Map.of(), ValueConstraints.forExhaustive());
        return new GeneratorContext(new SchemaDocument(new NullSchema(), Map.of()), new Random(1), config);
    }
}
