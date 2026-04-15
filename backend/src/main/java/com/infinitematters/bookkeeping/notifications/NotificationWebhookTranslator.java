package com.infinitematters.bookkeeping.notifications;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface NotificationWebhookTranslator {
    String providerKey();

    List<TranslatedProviderEvent> translate(JsonNode payload);
}
