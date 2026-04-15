package com.infinitematters.bookkeeping.notifications;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationProviderConfigurationValidator {
    private final String emailProvider;
    private final String sendGridApiKey;
    private final String sendGridFromEmail;
    private final String sendGridWebhookPublicKey;

    public NotificationProviderConfigurationValidator(
            @Value("${bookkeeping.notifications.email.provider:logging}") String emailProvider,
            @Value("${bookkeeping.notifications.email.sendgrid.api-key:}") String sendGridApiKey,
            @Value("${bookkeeping.notifications.email.sendgrid.from-email:}") String sendGridFromEmail,
            @Value("${bookkeeping.notifications.webhooks.sendgrid.public-key:}") String sendGridWebhookPublicKey) {
        this.emailProvider = emailProvider;
        this.sendGridApiKey = sendGridApiKey;
        this.sendGridFromEmail = sendGridFromEmail;
        this.sendGridWebhookPublicKey = sendGridWebhookPublicKey;
    }

    @PostConstruct
    void validate() {
        if (!"sendgrid".equalsIgnoreCase(emailProvider)) {
            return;
        }
        require(sendGridApiKey, "bookkeeping.notifications.email.sendgrid.api-key");
        require(sendGridFromEmail, "bookkeeping.notifications.email.sendgrid.from-email");
        require(sendGridWebhookPublicKey, "bookkeeping.notifications.webhooks.sendgrid.public-key");
    }

    private void require(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required notification configuration: " + propertyName);
        }
    }
}
