package com.example.di;

/**
 * Lifecycle scope of a bean.
 */
public enum Scope {

    /** One shared instance per container, eagerly destroyed on close. */
    SINGLETON,

    /** A new instance on every lookup or injection point. */
    PROTOTYPE
}
