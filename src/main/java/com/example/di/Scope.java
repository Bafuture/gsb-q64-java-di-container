package com.example.di;

public enum Scope {
    /** One shared instance per container, created eagerly at startup. */
    SINGLETON,
    /** A new instance is created for every injection point and every lookup. */
    PROTOTYPE
}
