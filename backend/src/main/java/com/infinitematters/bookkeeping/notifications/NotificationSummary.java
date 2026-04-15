package com.infinitematters.bookkeeping.notifications;

import java.time.Instant;
import java.util.UUID;

public record NotificationSummary(
        UUID id,
        UUID workflowTaskId,
        UUID userId,
        NotificationCategory category,
        NotificationChannel channel,
        NotificationStatus status,
        NotificationDeliveryState deliveryState,
        String message,
        String referenceType,
        String referenceId,
        String recipientEmail,
        String providerName,
        String providerMessageId,
        int attemptCount,
        String lastError,
        String lastFailureCode,
        DeadLetterResolutionStatus deadLetterResolutionStatus,
        DeadLetterResolutionReasonCode deadLetterResolutionReasonCode,
        String deadLetterResolutionNote,
        Instant deadLetterResolvedAt,
        UUID deadLetterResolvedByUserId,
        Instant scheduledFor,
        Instant lastAttemptedAt,
        Instant sentAt,
        Instant createdAt) {
    public static NotificationSummary from(Notification notification) {
        return new NotificationSummary(
                notification.getId(),
                notification.getWorkflowTask() != null ? notification.getWorkflowTask().getId() : null,
                notification.getUser() != null ? notification.getUser().getId() : null,
                notification.getCategory(),
                notification.getChannel(),
                notification.getStatus(),
                notification.getDeliveryState(),
                notification.getMessage(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.resolvedRecipientEmail(),
                notification.getProviderName(),
                notification.getProviderMessageId(),
                notification.getAttemptCount(),
                notification.getLastError(),
                notification.getLastFailureCode(),
                notification.getDeadLetterResolutionStatus(),
                notification.getDeadLetterResolutionReasonCode(),
                notification.getDeadLetterResolutionNote(),
                notification.getDeadLetterResolvedAt(),
                notification.getDeadLetterResolvedByUser() != null ? notification.getDeadLetterResolvedByUser().getId() : null,
                notification.getScheduledFor(),
                notification.getLastAttemptedAt(),
                notification.getSentAt(),
                notification.getCreatedAt());
    }
}
