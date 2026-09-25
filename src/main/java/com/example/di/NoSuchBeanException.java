package com.example.di;

/**
 * Thrown when no registered bean satisfies a requested type or name.
 */
public class NoSuchBeanException extends DiException {

    public NoSuchBeanException(String message) {
        super(message);
    }
}
