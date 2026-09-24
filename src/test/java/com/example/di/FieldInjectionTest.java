package com.example.di;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FieldInjectionTest {

    interface Clock {
        String zone();
    }

    @Component
    static class UtcClock implements Clock {
        @Override
        public String zone() {
            return "UTC";
        }
    }

    @Component(name = "localClock")
    static class LocalClock implements Clock {
        @Override
        public String zone() {
            return "local";
        }
    }

    @Component
    static class Scheduler {
        @Inject
        Clock clock;
    }

    @Component
    static class Reporter {
        @Inject
        @Qualifier("localClock")
        private Clock clock;

        Clock clock() {
            return clock;
        }
    }

    @Test
    void injectsFieldByType() {
        try (Container container = new Container()) {
            container.register(UtcClock.class, Scheduler.class);
            container.start();

            assertThat(container.getBean(Scheduler.class).clock).isInstanceOf(UtcClock.class);
            assertThat(container.getBean(Scheduler.class).clock.zone()).isEqualTo("UTC");
        }
    }

    @Test
    void injectsPrivateFieldByName() {
        try (Container container = new Container()) {
            container.register(UtcClock.class, LocalClock.class, Reporter.class);
            container.start();

            assertThat(container.getBean(Reporter.class).clock()).isInstanceOf(LocalClock.class);
        }
    }
}
