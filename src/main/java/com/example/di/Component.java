package com.example.di;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a bean managed by {@link DiContainer}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {

    /**
     * Bean name used for name-based lookup and {@link Named} qualifiers.
     * Defaults to the decapitalized simple class name.
     */
    String value() default "";

    /**
     * Bean scope. Defaults to {@link Scope#SINGLETON}.
     */
    Scope scope() default Scope.SINGLETON;
}
