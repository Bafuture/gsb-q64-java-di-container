package com.example.di;

/** Base runtime exception for all container configuration and creation failures. */
public class ContainerException extends RuntimeException {
    public ContainerException(String message) {
        super(message);
    }

    public ContainerException(String message, Throwable cause) {
        super(message, cause);
    }
}
