package com.infinitematters.bookkeeping.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class SendGridWebhookTranslator implements NotificationWebhookTranslator {
    @Override
    public String providerKey() {
        return "sendgrid";
    }

    @Override
    public List<TranslatedProviderEvent> translate(JsonNode payload) {
        if (!payload.isArray()) {
            throw new IllegalArgumentException("SendGrid webhook payload must be an array");
        }

        List<TranslatedProviderEvent> events = new ArrayList<>();
        for (JsonNode item : payload) {
            String providerMessageId = textValue(item, "sg_message_id");
            if (providerMessageId == null) {
                providerMessageId = textValue(item, "providerMessageId");
            }
            if (providerMessageId == null) {
                throw new IllegalArgumentException("SendGrid webhook event missing sg_message_id");
            }
            String eventType = normalizeEventType(textRequired(item, "event"));
            String externalEventId = textValue(item, "sg_event_id");
            Instant occurredAt = item.hasNonNull("timestamp")
                    ? Instant.ofEpochSecond(item.get("timestamp").asLong())
                    : Instant.now();
            events.add(new TranslatedProviderEvent(
                    "sendgrid",
                    providerMessageId,
                    eventType,
                    externalEventId,
                    occurredAt,
                    truncate(item.toString())));
        }
        return events;
    }

    private String normalizeEventType(String providerEvent) {
        return switch (providerEvent.toLowerCase()) {
            case "processed", "deferred" -> "ACCEPTED";
            case "delivered" -> "DELIVERED";
            case "bounce", "dropped" -> "BOUNCED";
            case "spamreport" -> "COMPLAINED";
            case "blocked" -> "FAILED";
            default -> "FAILED";
        };
    }

    private String textRequired(JsonNode node, String fieldName) {
        String value = textValue(node, fieldName);
        if (value == null) {
            throw new IllegalArgumentException("SendGrid webhook event missing " + fieldName);
        }
        return value;
    }

    private String textValue(JsonNode node, String fieldName) {
        return node.hasNonNull(fieldName) ? node.get(fieldName).asText() : null;
    }

    private String truncate(String value) {
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
