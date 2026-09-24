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
    static class Consumer {
        final PrototypeService first;
        final PrototypeService second;
        final SingletonService singletonFirst;
        final SingletonService singletonSecond;

        @Inject
        Consumer(PrototypeService first, PrototypeService second,
                 SingletonService singletonFirst, SingletonService singletonSecond) {
            this.first = first;
            this.second = second;
            this.singletonFirst = singletonFirst;
            this.singletonSecond = singletonSecond;
        }
    }

    @Test
    void singletonScopeReturnsTheSameInstance() {
        try (Container container = new Container()) {
            container.register(SingletonService.class, PrototypeService.class, Consumer.class);
            container.start();

            assertThat(container.getBean(SingletonService.class))
                    .isSameAs(container.getBean(SingletonService.class));

            Consumer consumer = container.getBean(Consumer.class);
            assertThat(consumer.singletonFirst).isSameAs(consumer.singletonSecond);
        }
    }

    @Test
    void prototypeScopeCreatesANewInstanceEveryTime() {
        try (Container container = new Container()) {
            container.register(SingletonService.class, PrototypeService.class, Consumer.class);
            container.start();

            assertThat(container.getBean(PrototypeService.class))
                    .isNotSameAs(container.getBean(PrototypeService.class));

            Consumer consumer = container.getBean(Consumer.class);
            assertThat(consumer.first).isNotSameAs(consumer.second);
        }
    }
}
