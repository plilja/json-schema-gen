package io.github.gjuton.errors;

/**
 * Thrown when the generator cannot produce a value that satisfies the schema.
 * This typically indicates either an over-constrained schema (no valid value
 * exists) or one where the generator's random search exhausted its retry
 * budget without finding one.
 *
 * <p>The exception message describes the specific constraint that could not
 * be satisfied.
 */
public class UnsatisfiableSchemaException extends RuntimeException {

    public UnsatisfiableSchemaException(String message) {
        super(message);
    }

    public UnsatisfiableSchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
