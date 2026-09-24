package com.example.di;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a class as a container-managed bean. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {
    /** Bean name. Defaults to the decapitalized simple class name. */
    String name() default "";

    Scope scope() default Scope.SINGLETON;
}
