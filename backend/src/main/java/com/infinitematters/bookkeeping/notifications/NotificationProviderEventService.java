package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.security.AccessDeniedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class NotificationProviderEventService {
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryEventRepository deliveryEventRepository;
    private final NotificationSuppressionService suppressionService;
    private final AuditService auditService;

    public NotificationProviderEventService(NotificationRepository notificationRepository,
                                            NotificationDeliveryEventRepository deliveryEventRepository,
                                            NotificationSuppressionService suppressionService,
                                            AuditService auditService) {
        this.notificationRepository = notificationRepository;
        this.deliveryEventRepository = deliveryEventRepository;
        this.suppressionService = suppressionService;
        this.auditService = auditService;
    }

    @Transactional
    public NotificationSummary ingestEvent(String providerName,
                                           String providerMessageId,
                                           String eventType,
                                           String externalEventId,
                                           Instant occurredAt,
                                           String payloadSummary,
                                           String rawPayload,
                                           VerifiedProviderEvent verifiedProviderEvent) {
        if (externalEventId != null && deliveryEventRepository.existsByExternalEventId(externalEventId)) {
            NotificationDeliveryEvent existing = deliveryEventRepository.findByExternalEventId(externalEventId)
                    .orElseThrow(() -> new IllegalStateException("Existing provider event not found"));
            return NotificationSummary.from(existing.getNotification());
        }

        Notification notification = notificationRepository
                .findByProviderNameAndProviderMessageId(providerName, providerMessageId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider notification"));

        NotificationDeliveryEvent event = new NotificationDeliveryEvent();
        event.setNotification(notification);
        event.setProviderName(providerName);
        event.setProviderMessageId(providerMessageId);
        event.setEventType(eventType);
        event.setExternalEventId(externalEventId);
        event.setPayloadSummary(payloadSummary);
        event.setRawPayload(truncate(rawPayload, 4000));
        event.setVerificationMethod(verifiedProviderEvent != null ? verifiedProviderEvent.verificationMethod() : null);
        event.setVerificationReference(verifiedProviderEvent != null ? verifiedProviderEvent.verificationReference() : null);
        event.setOccurredAt(occurredAt != null ? occurredAt : Instant.now());
        deliveryEventRepository.save(event);

        applyEvent(notification, normalizeEventType(eventType), event.getOccurredAt());
        notificationRepository.save(notification);
        auditService.record(notification.getOrganization() != null ? notification.getOrganization().getId() : null,
                "NOTIFICATION_PROVIDER_EVENT_INGESTED",
                "notification",
                notification.getId().toString(),
                eventType + " ingested for provider delivery");
        return NotificationSummary.from(notification);
    }

    private void applyEvent(Notification notification, String normalizedEventType, Instant eventAt) {
        switch (normalizedEventType) {
            case "DELIVERED" -> notification.setDeliveryState(NotificationDeliveryState.DELIVERED);
            case "ACCEPTED" -> notification.setDeliveryState(NotificationDeliveryState.ACCEPTED);
            case "BOUNCED" -> {
                notification.setDeliveryState(NotificationDeliveryState.BOUNCED);
                notification.setStatus(NotificationStatus.FAILED);
                notification.setLastError("Provider reported bounce");
                suppressIfPossible(notification, "BOUNCED", eventAt);
            }
            case "COMPLAINED" -> {
                notification.setDeliveryState(NotificationDeliveryState.COMPLAINED);
                notification.setStatus(NotificationStatus.FAILED);
                notification.setLastError("Provider reported complaint");
                suppressIfPossible(notification, "COMPLAINED", eventAt);
            }
            case "FAILED" -> {
                notification.setDeliveryState(NotificationDeliveryState.FAILED);
                notification.setStatus(NotificationStatus.FAILED);
                notification.setLastError("Provider reported delivery failure");
            }
            default -> throw new IllegalArgumentException("Unsupported provider event type: " + normalizedEventType);
        }
    }

    private String normalizeEventType(String eventType) {
        return eventType.trim().toUpperCase();
    }

    private void suppressIfPossible(Notification notification, String reason, Instant eventAt) {
        String recipientEmail = notification.resolvedRecipientEmail();
        if (recipientEmail == null) {
            return;
        }
        suppressionService.suppress(
                recipientEmail,
                notification.getProviderName() != null ? notification.getProviderName() : "unknown",
                reason,
                notification,
                eventAt);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
