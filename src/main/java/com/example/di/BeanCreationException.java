package com.example.di;

/**
 * Thrown when a bean instance cannot be constructed, injected or initialized.
 */
public class BeanCreationException extends DiException {

    public BeanCreationException(String message) {
        super(message);
    }

    public BeanCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
