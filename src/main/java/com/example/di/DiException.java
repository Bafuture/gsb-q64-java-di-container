package com.example.di;

/**
 * Base unchecked exception for all container failures.
 */
public class DiException extends RuntimeException {

    public DiException(String message) {
        super(message);
    }

    public DiException(String message, Throwable cause) {
        super(message, cause);
    }
}
