package com.example.di;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LifecycleTest {

    static final List<String> EVENTS = new ArrayList<>();

    @Component
    static class Repository {
        @PostConstruct
        void open() {
            EVENTS.add("repository:init");
        }

        @PreDestroy
        void shutdown() {
            EVENTS.add("repository:destroy");
        }
    }

    @Component
    static class Service {
        @Inject
        Service(Repository repository) {
        }

        @PostConstruct
        void warmUp() {
            EVENTS.add("service:init");
        }

        @PreDestroy
        void coolDown() {
            EVENTS.add("service:destroy");
        }
    }

    @Component(scope = Scope.PROTOTYPE)
    static class PrototypeBean {
        @PostConstruct
        void init() {
            EVENTS.add("prototype:init");
        }
    }

    @Test
    void runsPostConstructAfterInjectionAndPreDestroyInReverseDependencyOrder() {
        EVENTS.clear();
        Container container = new Container();
        container.register(Repository.class, Service.class);
        container.start();

        assertThat(EVENTS).containsExactly("repository:init", "service:init");

        container.close();

        assertThat(EVENTS).containsExactly(
                "repository:init", "service:init",
                "service:destroy", "repository:destroy");
    }

    @Test
    void runsPostConstructOnEveryPrototypeCreation() {
        EVENTS.clear();
        try (Container container = new Container()) {
            container.register(PrototypeBean.class);
            container.start();

            container.getBean(PrototypeBean.class);
            container.getBean(PrototypeBean.class);

            assertThat(EVENTS).containsExactly("prototype:init", "prototype:init");
        }
    }
}
