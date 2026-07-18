package io.github.gjuton.internal.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Graph algorithms.
 */
public final class GraphUtil {

    private GraphUtil() {
    }

    /**
     * Returns a topological ordering of {@code nodes} such that for every
     * edge {@code u → v} in {@code edges}, {@code u} appears before
     * {@code v}. Nodes not reachable from any edge (or forming a cycle)
     * are appended at the end in their original iteration order.
     *
     * @param nodes the full set of nodes to order
     * @param edges adjacency list — each key maps to the list of nodes
     *     it must precede
     */
    public static <T> List<T> topologicalSort(Collection<T> nodes, Map<T, List<T>> edges) {
        var inDegree = new HashMap<T, Integer>();
        var adjacency = new HashMap<T, List<T>>();
        for (var node : nodes) {
            inDegree.put(node, 0);
            adjacency.put(node, new ArrayList<>());
        }
        for (var entry : edges.entrySet()) {
            if (!inDegree.containsKey(entry.getKey())) {
                continue;
            }
            for (var target : entry.getValue()) {
                if (!inDegree.containsKey(target)) {
                    continue;
                }
                adjacency.get(entry.getKey()).add(target);
                inDegree.merge(target, 1, Integer::sum);
            }
        }

        var queue = new ArrayDeque<T>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        var sorted = new ArrayList<T>();
        while (!queue.isEmpty()) {
            var node = queue.poll();
            sorted.add(node);
            for (var target : adjacency.get(node)) {
                if (inDegree.merge(target, -1, Integer::sum) == 0) {
                    queue.add(target);
                }
            }
        }

        var sortedSet = new HashSet<>(sorted);
        for (var node : nodes) {
            if (!sortedSet.contains(node)) {
                sorted.add(node);
            }
        }
        return sorted;
    }
}
