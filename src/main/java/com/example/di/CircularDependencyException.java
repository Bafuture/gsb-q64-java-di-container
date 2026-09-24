package com.example.di;

/** Thrown when constructor-injected beans form a dependency cycle. */
public class CircularDependencyException extends ContainerException {
    public CircularDependencyException(String message) {
        super(message);
    }
}
