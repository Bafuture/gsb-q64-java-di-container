package com.example.di;

/**
 * Thrown at startup when an injection point matches more than one bean
 * and no {@link Named} qualifier disambiguates it.
 */
public class AmbiguousBeanException extends DiException {

    public AmbiguousBeanException(String message) {
        super(message);
    }
}
