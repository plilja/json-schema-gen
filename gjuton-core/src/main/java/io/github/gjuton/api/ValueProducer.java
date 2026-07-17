package io.github.gjuton.api;

/**
 * Supplies the value for a specific position in generated JSON, replacing
 * whatever the generator would otherwise produce there. Registered against a
 * path with {@link Gjuton#withProducer(String, ValueProducer)}
 * and invoked afresh on every {@link Gjuton#generate()} that
 * reaches that path, so it may return a different value each time.
 *
 * <p>The returned object may be anything that serialises to JSON — scalar,
 * {@code Collection}, {@code Map}, or bean. It is inserted as-is and is
 * <em>not</em> validated against the schema at its path.
 *
 * <p>Composes with a data-faker library in one line:
 * <pre>{@code
 * generator.withProducer("$.email", faker.internet()::emailAddress);
 * }</pre>
 */
@FunctionalInterface
public interface ValueProducer {

    /**
     * Returns the value to place at the registered path for the current
     * {@link Gjuton#generate()} call.
     */
    Object produce();
}
