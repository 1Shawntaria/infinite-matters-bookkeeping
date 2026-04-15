package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class NotificationDispatchService {
    private final NotificationRepository notificationRepository;
    private final List<NotificationDeliveryGateway> deliveryGateways;
    private final NotificationSuppressionService suppressionService;
    private final AuditService auditService;
    private final int maxAttempts;
    private final Duration retryDelay;

    public NotificationDispatchService(NotificationRepository notificationRepository,
                                       List<NotificationDeliveryGateway> deliveryGateways,
                                       NotificationSuppressionService suppressionService,
                                       AuditService auditService,
                                       @Value("${bookkeeping.notifications.dispatch.max-attempts:3}") int maxAttempts,
                                       @Value("${bookkeeping.notifications.dispatch.retry-delay:PT10M}") Duration retryDelay) {
        this.notificationRepository = notificationRepository;
        this.deliveryGateways = deliveryGateways;
        this.suppressionService = suppressionService;
        this.auditService = auditService;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
    }

    @Transactional
    public int dispatchPendingNotifications() {
        List<Notification> pendingNotifications = notificationRepository
                .findTop100ByStatusAndScheduledForBeforeOrderByCreatedAtAsc(NotificationStatus.PENDING, Instant.now());
        int deliveredCount = 0;
        for (Notification notification : pendingNotifications) {
            if (isSuppressed(notification)) {
                markSuppressed(notification);
                notificationRepository.save(notification);
                continue;
            }
            NotificationDeliveryGateway gateway = deliveryGateways.stream()
                    .filter(candidate -> candidate.supports(notification.getChannel()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No delivery gateway for channel " + notification.getChannel()));
            Instant attemptedAt = Instant.now();
            notification.setLastAttemptedAt(attemptedAt);
            notification.setAttemptCount(notification.getAttemptCount() + 1);
            try {
                NotificationDeliveryReceipt receipt = gateway.deliver(notification);
                notification.setStatus(NotificationStatus.SENT);
                notification.setDeliveryState(NotificationDeliveryState.ACCEPTED);
                notification.setSentAt(attemptedAt);
                notification.setLastError(null);
                notification.setLastFailureCode(null);
                notification.setDeadLetterResolutionStatus(null);
                notification.setDeadLetterResolutionReasonCode(null);
                notification.setDeadLetterResolutionNote(null);
                notification.setDeadLetterResolvedAt(null);
                notification.setDeadLetterResolvedByUser(null);
                notification.setProviderName(receipt.providerName());
                notification.setProviderMessageId(receipt.providerMessageId());
            } catch (RuntimeException exception) {
                handleDeliveryFailure(notification, exception, attemptedAt);
            }
            notificationRepository.save(notification);
            if (notification.getStatus() == NotificationStatus.SENT) {
                auditService.record(notification.getOrganization() != null ? notification.getOrganization().getId() : null,
                        "NOTIFICATION_DISPATCHED",
                        "notification",
                        notification.getId().toString(),
                        notification.getChannel() + " notification dispatched");
                deliveredCount++;
            }
        }
        return deliveredCount;
    }

    private boolean isSuppressed(Notification notification) {
        return notification.getChannel() == NotificationChannel.EMAIL
                && notification.resolvedRecipientEmail() != null
                && suppressionService.isSuppressed(
                        notification.resolvedRecipientEmail(),
                        "sendgrid");
    }

    private void markSuppressed(Notification notification) {
        notification.setStatus(NotificationStatus.FAILED);
        notification.setDeliveryState(NotificationDeliveryState.FAILED);
        notification.setLastError("Recipient suppressed by provider");
        notification.setLastFailureCode("RECIPIENT_SUPPRESSED");
        notification.setDeadLetterResolutionStatus(DeadLetterResolutionStatus.OPEN);
        notification.setDeadLetterResolutionReasonCode(null);
        notification.setDeadLetterResolutionNote(null);
        notification.setDeadLetterResolvedAt(null);
        notification.setDeadLetterResolvedByUser(null);
        auditService.record(notification.getOrganization() != null ? notification.getOrganization().getId() : null,
                "NOTIFICATION_DELIVERY_SUPPRESSED",
                "notification",
                notification.getId().toString(),
                "Notification skipped because recipient is suppressed");
    }

    private void handleDeliveryFailure(Notification notification, RuntimeException exception, Instant attemptedAt) {
        notification.setLastError(truncate(exception.getMessage()));
        notification.setLastFailureCode(exception instanceof NotificationDeliveryException deliveryException
                ? deliveryException.failureCode()
                : "DELIVERY_ERROR");
        notification.setProviderName(null);
        notification.setProviderMessageId(null);
        if (exception instanceof NotificationDeliveryException deliveryException && !deliveryException.retryable()) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setDeliveryState(NotificationDeliveryState.FAILED);
            notification.setDeadLetterResolutionStatus(DeadLetterResolutionStatus.OPEN);
            notification.setDeadLetterResolutionReasonCode(null);
            notification.setDeadLetterResolutionNote(null);
            notification.setDeadLetterResolvedAt(null);
            notification.setDeadLetterResolvedByUser(null);
            auditService.record(notification.getOrganization() != null ? notification.getOrganization().getId() : null,
                    "NOTIFICATION_DISPATCH_PERMANENT_FAILURE",
                    "notification",
                    notification.getId().toString(),
                    deliveryException.failureCode() + ": " + notification.getLastError());
            return;
        }
        if (notification.getAttemptCount() >= maxAttempts) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setDeliveryState(NotificationDeliveryState.FAILED);
            notification.setDeadLetterResolutionStatus(DeadLetterResolutionStatus.OPEN);
            notification.setDeadLetterResolutionReasonCode(null);
            notification.setDeadLetterResolutionNote(null);
            notification.setDeadLetterResolvedAt(null);
            notification.setDeadLetterResolvedByUser(null);
            auditService.record(notification.getOrganization() != null ? notification.getOrganization().getId() : null,
                    "NOTIFICATION_DISPATCH_FAILED",
                    "notification",
                    notification.getId().toString(),
                    "Delivery failed permanently after " + notification.getAttemptCount() + " attempts");
            return;
        }
        notification.setStatus(NotificationStatus.PENDING);
        notification.setDeliveryState(NotificationDeliveryState.PENDING);
        notification.setScheduledFor(attemptedAt.plus(retryDelay));
        String eventType = exception instanceof NotificationDeliveryException deliveryException
                && "RATE_LIMITED".equals(deliveryException.failureCode())
                ? "NOTIFICATION_DISPATCH_RATE_LIMITED"
                : "NOTIFICATION_DISPATCH_RETRY_SCHEDULED";
        auditService.record(notification.getOrganization() != null ? notification.getOrganization().getId() : null,
                eventType,
                "notification",
                notification.getId().toString(),
                "Delivery failed; retry " + notification.getAttemptCount() + " scheduled");
    }

    private String truncate(String message) {
        if (message == null || message.length() <= 1000) {
            return message;
        }
        return message.substring(0, 1000);
    }
}
