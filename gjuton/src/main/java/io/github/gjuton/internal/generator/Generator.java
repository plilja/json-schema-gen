package io.github.gjuton.internal.generator;

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
}
