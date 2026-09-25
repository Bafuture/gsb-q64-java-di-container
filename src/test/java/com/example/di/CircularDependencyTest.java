package com.example.di;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CircularDependencyTest {

    @Component
    static class A {
        @Inject
        A(B b) {
        }
    }

    @Component
    static class B {
        @Inject
        B(C c) {
        }
    }

    @Component
    static class C {
        @Inject
        C(A a) {
        }
    }

    @Component
    static class Self {
        @Inject
        Self(Self self) {
        }
    }

    @Test
    void detectsConstructorCycleAtStartupWithFullPath() {
        DiContainer container = new DiContainer();
        container.register(A.class, B.class, C.class);

        assertThatThrownBy(container::start)
                .isInstanceOf(CircularDependencyException.class)
                .hasMessageContaining("A -> B -> C -> A");
    }

    @Test
    void detectsSelfDependency() {
        DiContainer container = new DiContainer();
        container.register(Self.class);

        assertThatThrownBy(container::start)
                .isInstanceOf(CircularDependencyException.class)
                .hasMessageContaining("Self -> Self");
    }
}
