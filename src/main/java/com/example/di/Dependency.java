package com.example.di;

/**
 * A single injection point: a required type plus an optional name qualifier.
 */
record Dependency(Class<?> type, String name) {

    static Dependency of(Class<?> type, Named named) {
        return new Dependency(type, named == null ? null : named.value());
    }

    String describe() {
        return name == null
                ? "type '" + type.getSimpleName() + "'"
                : "type '" + type.getSimpleName() + "' named '" + name + "'";
    }
}
