package com.example.di;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScopeTest {

    @Component
    static class SingletonService {
    }

    @Component(scope = Scope.PROTOTYPE)
    static class PrototypeService {
    }

    @Component
    static class PrototypeConsumer {
        final PrototypeService first;
        final PrototypeService second;

        @Inject
        PrototypeConsumer(PrototypeService first, PrototypeService second) {
            this.first = first;
            this.second = second;
        }
    }

    @Test
    void singletonScopeReturnsTheSameInstance() {
        try (DiContainer container = new DiContainer()) {
            container.register(SingletonService.class);
            container.start();

            assertThat(container.getBean(SingletonService.class))
                    .isSameAs(container.getBean(SingletonService.class));
        }
    }

    @Test
    void prototypeScopeReturnsANewInstanceEachTime() {
        try (DiContainer container = new DiContainer()) {
            container.register(PrototypeService.class, PrototypeConsumer.class);
            container.start();

            assertThat(container.getBean(PrototypeService.class))
                    .isNotSameAs(container.getBean(PrototypeService.class));

            PrototypeConsumer consumer = container.getBean(PrototypeConsumer.class);
            assertThat(consumer.first).isNotSameAs(consumer.second);
        }
    }
}
