package com.example.di;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FieldInjectionTest {

    @Component
    static class UserRepository {
        String findName() {
            return "alice";
        }
    }

    @Component
    static class UserService {
        @Inject
        private UserRepository repository;
    }

    @Test
    void injectsPrivateFieldByType() {
        try (DiContainer container = new DiContainer()) {
            container.register(UserService.class, UserRepository.class);
            container.start();

            UserService service = container.getBean(UserService.class);

            assertThat(service.repository).isNotNull();
            assertThat(service.repository.findName()).isEqualTo("alice");
        }
    }

    interface Cache {
        String region();
    }

    @Component("localCache")
    static class LocalCache implements Cache {
        @Override
        public String region() {
            return "local";
        }
    }

    @Component("remoteCache")
    static class RemoteCache implements Cache {
        @Override
        public String region() {
            return "remote";
        }
    }

    @Component
    static class CacheConsumer {
        @Inject
        @Named("remoteCache")
        Cache cache;
    }

    @Test
    void injectsFieldByName() {
        try (DiContainer container = new DiContainer()) {
            container.register(CacheConsumer.class, LocalCache.class, RemoteCache.class);
            container.start();

            CacheConsumer consumer = container.getBean(CacheConsumer.class);

            assertThat(consumer.cache).isInstanceOf(RemoteCache.class);
            assertThat(consumer.cache.region()).isEqualTo("remote");
        }
    }

    @Component
    static class BaseHandler {
        @Inject
        UserRepository repository;
    }

    @Component
    static class ConcreteHandler extends BaseHandler {
    }

    @Test
    void injectsFieldsDeclaredOnSuperclasses() {
        try (DiContainer container = new DiContainer()) {
            container.register(ConcreteHandler.class, UserRepository.class);
            container.start();

            assertThat(container.getBean(ConcreteHandler.class).repository).isNotNull();
        }
    }
}
