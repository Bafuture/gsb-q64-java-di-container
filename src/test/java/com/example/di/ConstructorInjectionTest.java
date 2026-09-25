package com.example.di;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConstructorInjectionTest {

    interface GreetingService {
        String greet();
    }

    @Component
    static class DefaultGreetingService implements GreetingService {
        @Override
        public String greet() {
            return "hello";
        }
    }

    @Component
    static class GreetingController {
        private final GreetingService service;

        @Inject
        GreetingController(GreetingService service) {
            this.service = service;
        }
    }

    @Test
    void injectsConstructorDependencyByType() {
        try (DiContainer container = new DiContainer()) {
            container.register(GreetingController.class, DefaultGreetingService.class);
            container.start();

            GreetingController controller = container.getBean(GreetingController.class);

            assertThat(controller.service).isInstanceOf(DefaultGreetingService.class);
            assertThat(controller.service.greet()).isEqualTo("hello");
        }
    }

    interface PaymentGateway {
        String id();
    }

    @Component("alipay")
    static class AlipayGateway implements PaymentGateway {
        @Override
        public String id() {
            return "alipay";
        }
    }

    @Component("wechat")
    static class WechatGateway implements PaymentGateway {
        @Override
        public String id() {
            return "wechat";
        }
    }

    @Component
    static class CheckoutService {
        private final PaymentGateway gateway;

        @Inject
        CheckoutService(@Named("wechat") PaymentGateway gateway) {
            this.gateway = gateway;
        }
    }

    @Test
    void injectsConstructorDependencyByName() {
        try (DiContainer container = new DiContainer()) {
            container.register(CheckoutService.class, AlipayGateway.class, WechatGateway.class);
            container.start();

            CheckoutService service = container.getBean(CheckoutService.class);

            assertThat(service.gateway).isInstanceOf(WechatGateway.class);
            assertThat(service.gateway.id()).isEqualTo("wechat");
        }
    }

    @Test
    void looksUpBeansByName() {
        try (DiContainer container = new DiContainer()) {
            container.register(AlipayGateway.class, WechatGateway.class);
            container.start();

            assertThat(container.getBean("alipay", PaymentGateway.class).id()).isEqualTo("alipay");
            assertThat(container.getBean("wechat")).isInstanceOf(WechatGateway.class);
        }
    }
}
