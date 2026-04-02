package com.infinitematters.bookkeeping.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationWebhookTranslationServiceTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    private NotificationWebhookTranslationService translationService;

    @BeforeEach
    void setUp() {
        translationService = new NotificationWebhookTranslationService(
                List.of(new SendGridWebhookTranslator(), new SesWebhookTranslator()));
    }

    @Test
    void translatesSendGridPayloadIntoNormalizedEvents() throws Exception {
        var payload = objectMapper.readTree("""
                [
                  {
                    "event": "processed",
                    "sg_message_id": "sg-123",
                    "sg_event_id": "evt-1",
                    "timestamp": 1772442000
                  },
                  {
                    "event": "delivered",
                    "sg_message_id": "sg-123",
                    "sg_event_id": "evt-2",
                    "timestamp": 1772442060
                  }
                ]
                """);

        List<TranslatedProviderEvent> events = translationService.translate("sendgrid", payload);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).providerName()).isEqualTo("sendgrid");
        assertThat(events.get(0).eventType()).isEqualTo("ACCEPTED");
        assertThat(events.get(0).providerMessageId()).isEqualTo("sg-123");
        assertThat(events.get(1).eventType()).isEqualTo("DELIVERED");
    }

    @Test
    void translatesSesPayloadIntoNormalizedEvent() throws Exception {
        var payload = objectMapper.readTree("""
                {
                  "eventType": "Delivery",
                  "eventId": "ses-evt-1",
                  "mail": {
                    "messageId": "ses-message-1",
                    "timestamp": "2026-03-31T12:00:00Z"
                  }
                }
                """);

        List<TranslatedProviderEvent> events = translationService.translate("ses", payload);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.providerName()).isEqualTo("ses");
            assertThat(event.providerMessageId()).isEqualTo("ses-message-1");
            assertThat(event.eventType()).isEqualTo("DELIVERED");
            assertThat(event.externalEventId()).isEqualTo("ses-evt-1");
            assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-03-31T12:00:00Z"));
        });
    }
}
