package com.infinitematters.bookkeeping.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmailNotificationProviderSelector {
    private final List<EmailNotificationProvider> providers;
    private final String configuredProviderKey;

    public EmailNotificationProviderSelector(List<EmailNotificationProvider> providers,
                                             @Value("${bookkeeping.notifications.email.provider:logging}")
                                             String configuredProviderKey) {
        this.providers = providers;
        this.configuredProviderKey = configuredProviderKey;
    }

    public EmailNotificationProvider selectedProvider() {
        return providers.stream()
                .filter(provider -> provider.providerKey().equalsIgnoreCase(configuredProviderKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unsupported email notification provider: " + configuredProviderKey));
    }
}
