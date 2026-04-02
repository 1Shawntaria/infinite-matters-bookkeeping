package com.infinitematters.bookkeeping.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinitematters.bookkeeping.notifications.NotificationProviderEventService;
import com.infinitematters.bookkeeping.notifications.NotificationSummary;
import com.infinitematters.bookkeeping.notifications.VerifiedProviderEvent;
import com.infinitematters.bookkeeping.notifications.NotificationWebhookSecurityService;
import com.infinitematters.bookkeeping.notifications.NotificationWebhookTranslationService;
import com.infinitematters.bookkeeping.notifications.TranslatedProviderEvent;
import com.infinitematters.bookkeeping.web.dto.NotificationProviderEventRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/providers/notifications")
public class NotificationProviderController {
    private final NotificationProviderEventService notificationProviderEventService;
    private final NotificationWebhookTranslationService notificationWebhookTranslationService;
    private final NotificationWebhookSecurityService notificationWebhookSecurityService;
    private final ObjectMapper objectMapper;

    public NotificationProviderController(NotificationProviderEventService notificationProviderEventService,
                                          NotificationWebhookTranslationService notificationWebhookTranslationService,
                                          NotificationWebhookSecurityService notificationWebhookSecurityService,
                                          ObjectMapper objectMapper) {
        this.notificationProviderEventService = notificationProviderEventService;
        this.notificationWebhookTranslationService = notificationWebhookTranslationService;
        this.notificationWebhookSecurityService = notificationWebhookSecurityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/events")
    public NotificationSummary ingestEvent(@RequestHeader("X-Provider-Webhook-Secret") String webhookSecret,
                                           @Valid @RequestBody NotificationProviderEventRequest request) {
        VerifiedProviderEvent verification = notificationWebhookSecurityService.verifyGenericSecret(webhookSecret);
        return notificationProviderEventService.ingestEvent(
                request.providerName(),
                request.providerMessageId(),
                request.eventType(),
                request.externalEventId(),
                request.occurredAt(),
                request.payloadSummary(),
                null,
                verification);
    }

    @PostMapping("/events/{providerKey}")
    public List<NotificationSummary> ingestProviderEvents(
            @RequestHeader(value = "X-Provider-Webhook-Secret", required = false) String webhookSecret,
            @RequestHeader(value = "X-Twilio-Email-Event-Webhook-Signature", required = false) String signatureHeader,
            @RequestHeader(value = "X-Twilio-Email-Event-Webhook-Timestamp", required = false) String timestampHeader,
            @PathVariable String providerKey,
            @RequestBody String payload) throws Exception {
        VerifiedProviderEvent verification = notificationWebhookSecurityService.verifyProviderRequest(
                providerKey,
                webhookSecret,
                payload,
                signatureHeader,
                timestampHeader);
        JsonNode parsedPayload = objectMapper.readTree(payload);
        List<TranslatedProviderEvent> events = notificationWebhookTranslationService.translate(providerKey, parsedPayload);
        return events.stream()
                .map(event -> notificationProviderEventService.ingestEvent(
                        event.providerName(),
                        event.providerMessageId(),
                        event.eventType(),
                        event.externalEventId(),
                        event.occurredAt(),
                        event.payloadSummary(),
                        payload,
                        verification))
                .toList();
    }
}
