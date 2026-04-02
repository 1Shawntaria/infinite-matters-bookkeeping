package com.infinitematters.bookkeeping.notifications;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNotificationProviderSelectorTests {

    @Test
    void selectsConfiguredProviderByKey() {
        EmailNotificationProviderSelector selector = new EmailNotificationProviderSelector(
                java.util.List.of(
                        new TestProvider("logging"),
                        new TestProvider("sendgrid")),
                "sendgrid");

        assertThat(selector.selectedProvider().providerKey()).isEqualTo("sendgrid");
    }

    private record TestProvider(String providerKey) implements EmailNotificationProvider {
        @Override
        public NotificationDeliveryReceipt send(Notification notification) {
            return new NotificationDeliveryReceipt(providerKey, providerKey + "-message");
        }
    }
}
