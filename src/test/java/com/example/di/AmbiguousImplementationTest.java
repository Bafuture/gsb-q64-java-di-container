package com.example.di;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AmbiguousImplementationTest {

    interface Notifier {
        String channel();
    }

    @Component
    static class EmailNotifier implements Notifier {
        @Override
        public String channel() {
            return "email";
        }
    }

    @Component
    static class SmsNotifier implements Notifier {
        @Override
        public String channel() {
            return "sms";
        }
    }

    @Component
    static class NotificationFacade {
        final Notifier notifier;

        @Inject
        NotificationFacade(Notifier notifier) {
            this.notifier = notifier;
        }
    }

    @Component
    static class QualifiedFacade {
        final Notifier notifier;

        @Inject
        QualifiedFacade(@Named("smsNotifier") Notifier notifier) {
            this.notifier = notifier;
        }
    }

    @Test
    void failsAtStartupWhenMultipleImplementationsAndNoQualifier() {
        DiContainer container = new DiContainer();
        container.register(NotificationFacade.class, EmailNotifier.class, SmsNotifier.class);

        assertThatThrownBy(container::start)
                .isInstanceOf(AmbiguousBeanException.class)
                .hasMessageContaining("Notifier")
                .hasMessageContaining("emailNotifier")
                .hasMessageContaining("smsNotifier");
    }

    @Test
    void failsOnDirectLookupOfAmbiguousType() {
        DiContainer container = new DiContainer();
        container.register(EmailNotifier.class, SmsNotifier.class);

        assertThatThrownBy(() -> container.getBean(Notifier.class))
                .isInstanceOf(AmbiguousBeanException.class);
    }

    @Test
    void resolvesUnambiguouslyWithNamedQualifier() {
        try (DiContainer container = new DiContainer()) {
            container.register(QualifiedFacade.class, EmailNotifier.class, SmsNotifier.class);
            container.start();

            assertThat(container.getBean(QualifiedFacade.class).notifier.channel()).isEqualTo("sms");
        }
    }
}
