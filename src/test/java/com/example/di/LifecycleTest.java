package com.example.di;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LifecycleTest {

    static final List<String> EVENTS = new ArrayList<>();

    @Component
    static class Database {
        boolean connected;

        @PostConstruct
        void connect() {
            connected = true;
            EVENTS.add("init:database");
        }

        @PreDestroy
        void disconnect() {
            EVENTS.add("destroy:database");
        }
    }

    @Component
    static class Repository {
        final Database database;
        boolean ready;

        @Inject
        Repository(Database database) {
            this.database = database;
        }

        @PostConstruct
        void warmUp() {
            ready = database.connected;
            EVENTS.add("init:repository");
        }

        @PreDestroy
        void coolDown() {
            EVENTS.add("destroy:repository");
        }
    }

    @Component(scope = Scope.PROTOTYPE)
    static class Ephemeral {
        @PreDestroy
        void cleanup() {
            EVENTS.add("destroy:ephemeral");
        }
    }

    @Test
    void runsInitAfterInjectionAndDestroysInReverseDependencyOrder() {
        EVENTS.clear();
        DiContainer container = new DiContainer();
        container.register(Repository.class, Database.class, Ephemeral.class);
        container.start();

        Repository repository = container.getBean(Repository.class);
        assertThat(repository.ready).isTrue();
        assertThat(EVENTS).containsExactly("init:database", "init:repository");

        container.getBean(Ephemeral.class);
        container.close();

        assertThat(EVENTS).containsExactly(
                "init:database", "init:repository",
                "destroy:repository", "destroy:database");
    }
}
