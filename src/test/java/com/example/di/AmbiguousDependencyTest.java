package com.example.di;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AmbiguousDependencyTest {

    interface PaymentGateway {
        String id();
    }

    @Component
    static class StripeGateway implements PaymentGateway {
        @Override
        public String id() {
            return "stripe";
        }
    }

    @Component
    static class PaypalGateway implements PaymentGateway {
        @Override
        public String id() {
            return "paypal";
        }
    }

    @Component
    static class CheckoutService {
        final PaymentGateway gateway;

        @Inject
        CheckoutService(PaymentGateway gateway) {
            this.gateway = gateway;
        }
    }

    @Component
    static class QualifiedCheckoutService {
        final PaymentGateway gateway;

        @Inject
        QualifiedCheckoutService(@Qualifier("paypalGateway") PaymentGateway gateway) {
            this.gateway = gateway;
        }
    }

    @Test
    void failsAtStartupWhenSeveralImplementationsExistAndNoNameIsGiven() {
        Container container = new Container();
        container.register(StripeGateway.class, PaypalGateway.class, CheckoutService.class);

        assertThatThrownBy(container::start)
                .isInstanceOf(ContainerException.class)
                .hasMessageContaining(PaymentGateway.class.getName())
                .hasMessageContaining("stripeGateway")
                .hasMessageContaining("paypalGateway");
    }

    @Test
    void resolvesUnambiguouslyWhenANameIsGiven() {
        try (Container container = new Container()) {
            container.register(StripeGateway.class, PaypalGateway.class, QualifiedCheckoutService.class);

            assertThatCode(container::start).doesNotThrowAnyException();
            assertThat(container.getBean(QualifiedCheckoutService.class).gateway)
                    .isInstanceOf(PaypalGateway.class);
        }
    }
}
