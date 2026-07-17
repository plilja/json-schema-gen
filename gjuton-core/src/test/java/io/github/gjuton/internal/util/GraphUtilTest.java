package io.github.gjuton.internal.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphUtilTest {

    @Test
    void linearChainIsOrderedCorrectly() {
        // when
        var result = GraphUtil.topologicalSort(
                List.of("a", "b", "c"),
                Map.of("a", List.of("b"), "b", List.of("c"))
        );

        // then
        assertThat(result).containsExactly("a", "b", "c");
    }

    @Test
    void diamondDagPutsDependentsAfterTriggers() {
        // when
        var result = GraphUtil.topologicalSort(
                List.of("a", "b", "c", "d"),
                Map.of("a", List.of("b", "c"), "b", List.of("d"), "c", List.of("d"))
        );

        // then
        assertThat(result.indexOf("a")).isLessThan(result.indexOf("b"));
        assertThat(result.indexOf("a")).isLessThan(result.indexOf("c"));
        assertThat(result.indexOf("b")).isLessThan(result.indexOf("d"));
        assertThat(result.indexOf("c")).isLessThan(result.indexOf("d"));
    }

    @Test
    void noEdgesReturnsAllNodes() {
        // when
        var result = GraphUtil.topologicalSort(
                List.of("x", "y", "z"),
                Map.of()
        );

        // then
        assertThat(result).containsExactlyInAnyOrder("x", "y", "z");
    }

    @Test
    void cycleNodesAreAppendedAtEnd() {
        // when
        var result = GraphUtil.topologicalSort(
                List.of("a", "b", "c"),
                Map.of("a", List.of("b"), "b", List.of("a"), "c", List.of())
        );

        // then
        assertThat(result).containsExactlyInAnyOrder("a", "b", "c");
        assertThat(result).contains("c");
    }

    @Test
    void edgesReferencingUnknownNodesAreIgnored() {
        // when
        var result = GraphUtil.topologicalSort(
                List.of("a", "b"),
                Map.of("a", List.of("b", "unknown"))
        );

        // then
        assertThat(result).containsExactlyInAnyOrder("a", "b");
        assertThat(result.indexOf("a")).isLessThan(result.indexOf("b"));
    }

    @Test
    void edgesFromUnknownNodesAreIgnored() {
        // when
        var result = GraphUtil.topologicalSort(
                List.of("a", "b"),
                Map.of("unknown", List.of("a"))
        );

        // then
        assertThat(result).containsExactlyInAnyOrder("a", "b");
    }
}
