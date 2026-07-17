package se.plilja.jsonschemagen.internal.generator;

import java.util.List;
import se.plilja.jsonschemagen.internal.model.Schema;

/**
 * Produces a value conforming to a JSON Schema construct.
 *
 * <p>Generators are stateful — successive calls to {@link #generate()} may
 * return different values (boundary values first, then random). Callers
 * must not assume idempotence.
 *
 * @param <R> the Java type of the generated value
 */
public interface Generator<R> {

    R generate();

    /**
     * How many of this generator's deliberate values have been emitted so far,
     * in {@code [0, totalCount()]}. Never decreases across {@link #generate()}
     * calls.
     *
     * <p>A <em>deliberate value</em> is one the generator is designed to emit on
     * purpose — each enum literal, each boundary value, both booleans, the
     * const value — as opposed to arbitrary random filler. It underpins the
     * coverage measure a caller can generate towards.
     */
    long emittedCount();

    /**
     * The total number of deliberate values this generator will emit, fixed for
     * its lifetime. Coverage of this generator is complete when
     * {@link #emittedCount()} equals this value. Zero when the generator has no
     * deliberate values of its own (for example a {@code $ref}, whose target
     * carries the count instead).
     */
    long totalCount();

    /**
     * The child schemas this generator produces values for using stable
     * identity — reached the same way on every {@link #generate()} call, so
     * their generators are shared and their coverage is counted once. Used to
     * eagerly instantiate the fixed set of generators that the coverage measure
     * sums over.
     *
     * <p>Constructs whose branch schemas are synthesised afresh per call
     * (combining keywords, {@code if}/{@code then}/{@code else}) expose no
     * structural children: their subtree's coverage folds into their own
     * counts.
     */
    default List<Schema> structuralChildren() {
        return List.of();
    }
}
