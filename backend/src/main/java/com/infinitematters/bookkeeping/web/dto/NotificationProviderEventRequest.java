package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record NotificationProviderEventRequest(
        @NotBlank String providerName,
        @NotBlank String providerMessageId,
        @NotBlank String eventType,
        String externalEventId,
        Instant occurredAt,
        String payloadSummary) {
}
