package com.infinitematters.bookkeeping.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationWebhookTranslationService {
    private final List<NotificationWebhookTranslator> translators;

    public NotificationWebhookTranslationService(List<NotificationWebhookTranslator> translators) {
        this.translators = translators;
    }

    public List<TranslatedProviderEvent> translate(String providerKey, JsonNode payload) {
        NotificationWebhookTranslator translator = translators.stream()
                .filter(candidate -> candidate.providerKey().equalsIgnoreCase(providerKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported notification webhook provider: " + providerKey));
        return translator.translate(payload);
    }
}
