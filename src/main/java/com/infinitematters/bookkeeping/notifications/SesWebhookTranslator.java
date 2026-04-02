package com.infinitematters.bookkeeping.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class SesWebhookTranslator implements NotificationWebhookTranslator {
    @Override
    public String providerKey() {
        return "ses";
    }

    @Override
    public List<TranslatedProviderEvent> translate(JsonNode payload) {
        JsonNode mail = payload.get("mail");
        if (mail == null || !mail.hasNonNull("messageId")) {
            throw new IllegalArgumentException("SES webhook payload missing mail.messageId");
        }

        String providerEvent = payload.hasNonNull("eventType")
                ? payload.get("eventType").asText()
                : payload.path("notificationType").asText(null);
        if (providerEvent == null) {
            throw new IllegalArgumentException("SES webhook payload missing eventType");
        }

        Instant occurredAt = mail.hasNonNull("timestamp")
                ? Instant.parse(mail.get("timestamp").asText())
                : Instant.now();

        return List.of(new TranslatedProviderEvent(
                "ses",
                mail.get("messageId").asText(),
                normalizeEventType(providerEvent),
                payload.path("eventId").asText(null),
                occurredAt,
                truncate(payload.toString())));
    }

    private String normalizeEventType(String providerEvent) {
        return switch (providerEvent.toUpperCase()) {
            case "SEND", "DELIVERYDELAY" -> "ACCEPTED";
            case "DELIVERY" -> "DELIVERED";
            case "BOUNCE" -> "BOUNCED";
            case "COMPLAINT" -> "COMPLAINED";
            default -> "FAILED";
        };
    }

    private String truncate(String value) {
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
