package com.infinitematters.bookkeeping.notifications;

import java.time.Instant;

public record TranslatedProviderEvent(
        String providerName,
        String providerMessageId,
        String eventType,
        String externalEventId,
        Instant occurredAt,
        String payloadSummary) {
}
