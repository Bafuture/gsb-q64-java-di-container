package com.example.di;

/**
 * Thrown at startup when constructor-injected beans form a dependency cycle.
 * The message contains the full dependency path, e.g. {@code A -> B -> C -> A}.
 */
public class CircularDependencyException extends DiException {

    public CircularDependencyException(String message) {
        super(message);
    }
}
