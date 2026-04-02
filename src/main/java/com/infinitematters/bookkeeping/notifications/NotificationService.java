package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskPriority;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskRepository;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final WorkflowTaskRepository workflowTaskRepository;
    private final OrganizationService organizationService;
    private final NotificationSuppressionService suppressionService;
    private final AuditService auditService;
    private final UserService userService;

    public NotificationService(NotificationRepository notificationRepository,
                               WorkflowTaskRepository workflowTaskRepository,
                               OrganizationService organizationService,
                               NotificationSuppressionService suppressionService,
                               AuditService auditService,
                               UserService userService) {
        this.notificationRepository = notificationRepository;
        this.workflowTaskRepository = workflowTaskRepository;
        this.organizationService = organizationService;
        this.suppressionService = suppressionService;
        this.auditService = auditService;
        this.userService = userService;
    }

    @Transactional
    public ReminderRunResult generateTaskReminders(UUID organizationId) {
        organizationService.get(organizationId);
        LocalDate today = LocalDate.now();
        Instant dayStart = today.atStartOfDay().toInstant(ZoneOffset.UTC);

        List<NotificationSummary> created = workflowTaskRepository
                .findByOrganizationIdAndStatusOrderByCreatedAtAsc(organizationId, WorkflowTaskStatus.OPEN)
                .stream()
                .filter(task -> needsReminder(task, today))
                .filter(task -> !notificationRepository.existsByWorkflowTaskIdAndStatusAndScheduledForAfter(
                        task.getId(), NotificationStatus.SENT, dayStart))
                .map(task -> createReminder(task, today))
                .toList();

        return new ReminderRunResult(created.size(), created);
    }

    @Transactional(readOnly = true)
    public List<NotificationSummary> listForOrganization(UUID organizationId) {
        organizationService.get(organizationId);
        return notificationRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(NotificationSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Notification requireNotification(UUID notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification: " + notificationId));
    }

    @Transactional(readOnly = true)
    public NotificationOperationsSummary operationsSummary(UUID organizationId) {
        organizationService.get(organizationId);
        long pendingCount = notificationRepository.countByOrganizationIdAndStatus(organizationId, NotificationStatus.PENDING);
        long failedCount = notificationRepository.countByOrganizationIdAndStatus(organizationId, NotificationStatus.FAILED);
        long bouncedCount = notificationRepository.countByOrganizationIdAndDeliveryState(organizationId, NotificationDeliveryState.BOUNCED)
                + notificationRepository.countByOrganizationIdAndDeliveryState(organizationId, NotificationDeliveryState.COMPLAINED);
        List<Notification> failedNotifications = notificationRepository
                .findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, NotificationStatus.FAILED);
        long deadLetterCount = failedNotifications.stream()
                .filter(this::isUnresolvedDeadLetter)
                .count();
        long acknowledgedDeadLetterCount = failedNotifications.stream()
                .filter(this::isDeadLetter)
                .filter(notification -> notification.getDeadLetterResolutionStatus() == DeadLetterResolutionStatus.ACKNOWLEDGED)
                .count();
        long resolvedDeadLetterCount = failedNotifications.stream()
                .filter(this::isDeadLetter)
                .filter(notification -> notification.getDeadLetterResolutionStatus() == DeadLetterResolutionStatus.RESOLVED)
                .count();
        List<NotificationSummary> recentResolvedDeadLetters = failedNotifications.stream()
                .filter(this::isDeadLetter)
                .filter(notification -> notification.getDeadLetterResolutionStatus() == DeadLetterResolutionStatus.RESOLVED)
                .limit(5)
                .map(NotificationSummary::from)
                .toList();
        List<NotificationSummary> attentionNotifications = notificationRepository
                .findTop10ByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                        organizationId,
                        List.of(NotificationStatus.PENDING, NotificationStatus.FAILED))
                .stream()
                .filter(notification -> notification.getStatus() != NotificationStatus.FAILED
                        || notification.getDeadLetterResolutionStatus() != DeadLetterResolutionStatus.RESOLVED)
                .map(NotificationSummary::from)
                .toList();
        long retryingCount = attentionNotifications.stream()
                .filter(notification -> notification.status() == NotificationStatus.PENDING)
                .filter(notification -> notification.attemptCount() > 0)
                .count();
        return new NotificationOperationsSummary(
                pendingCount,
                failedCount,
                bouncedCount,
                deadLetterCount,
                retryingCount,
                suppressionService.activeSuppressionCount(),
                attentionNotifications,
                new DeadLetterOperationsSummary(
                        deadLetterCount - acknowledgedDeadLetterCount,
                        acknowledgedDeadLetterCount,
                        resolvedDeadLetterCount,
                        recentResolvedDeadLetters));
    }

    @Transactional(readOnly = true)
    public List<NotificationSummary> deadLetters(UUID organizationId) {
        organizationService.get(organizationId);
        return notificationRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, NotificationStatus.FAILED)
                .stream()
                .filter(this::isUnresolvedDeadLetter)
                .map(NotificationSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationSummary> resolvedDeadLetters(UUID organizationId) {
        organizationService.get(organizationId);
        return notificationRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, NotificationStatus.FAILED)
                .stream()
                .filter(this::isDeadLetter)
                .filter(notification -> notification.getDeadLetterResolutionStatus() == DeadLetterResolutionStatus.RESOLVED)
                .limit(10)
                .map(NotificationSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeadLetterQueueSummary deadLetterQueue(UUID organizationId) {
        organizationService.get(organizationId);
        List<Notification> failedNotifications = notificationRepository
                .findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, NotificationStatus.FAILED)
                .stream()
                .filter(this::isDeadLetter)
                .toList();

        List<DeadLetterQueueItem> needsUnsuppress = failedNotifications.stream()
                .filter(this::isUnresolvedDeadLetter)
                .map(this::toQueueItem)
                .filter(item -> item.recommendedAction() == DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY)
                .toList();

        List<DeadLetterQueueItem> needsRetry = failedNotifications.stream()
                .filter(this::isUnresolvedDeadLetter)
                .map(this::toQueueItem)
                .filter(item -> item.recommendedAction() == DeadLetterRecommendedAction.RETRY_DELIVERY)
                .toList();

        List<DeadLetterQueueItem> acknowledged = failedNotifications.stream()
                .filter(notification -> notification.getDeadLetterResolutionStatus() == DeadLetterResolutionStatus.ACKNOWLEDGED)
                .map(this::toQueueItem)
                .toList();

        List<DeadLetterQueueItem> recentlyResolved = failedNotifications.stream()
                .filter(notification -> notification.getDeadLetterResolutionStatus() == DeadLetterResolutionStatus.RESOLVED)
                .limit(10)
                .map(this::toQueueItem)
                .toList();

        return new DeadLetterQueueSummary(needsRetry, needsUnsuppress, acknowledged, recentlyResolved);
    }

    @Transactional
    public NotificationSummary requeueFailedNotification(UUID organizationId, UUID notificationId) {
        organizationService.get(organizationId);
        Notification notification = notificationRepository.findByIdAndOrganizationId(notificationId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification: " + notificationId));
        if (notification.getStatus() != NotificationStatus.FAILED) {
            throw new IllegalArgumentException("Only failed notifications can be requeued");
        }
        resetForRequeue(notification);
        notification = notificationRepository.save(notification);
        auditService.record(organizationId,
                "NOTIFICATION_REQUEUED",
                "notification",
                notification.getId().toString(),
                "Failed notification requeued for delivery");
        return NotificationSummary.from(notification);
    }

    @Transactional
    public NotificationSummary acknowledgeDeadLetter(UUID organizationId,
                                                     UUID notificationId,
                                                     UUID actorUserId,
                                                     String note) {
        organizationService.get(organizationId);
        Notification notification = requireDeadLetter(organizationId, notificationId);
        AppUser actor = userService.get(actorUserId);
        notification.setDeadLetterResolutionStatus(DeadLetterResolutionStatus.ACKNOWLEDGED);
        notification.setDeadLetterResolutionReasonCode(null);
        notification.setDeadLetterResolutionNote(trimNote(note));
        notification.setDeadLetterResolvedAt(Instant.now());
        notification.setDeadLetterResolvedByUser(actor);
        notification = notificationRepository.save(notification);
        auditService.record(organizationId,
                "NOTIFICATION_DEAD_LETTER_ACKNOWLEDGED",
                "notification",
                notification.getId().toString(),
                notification.getDeadLetterResolutionNote() != null
                        ? notification.getDeadLetterResolutionNote()
                        : "Dead-letter notification acknowledged");
        return NotificationSummary.from(notification);
    }

    @Transactional
    public NotificationSummary resolveDeadLetter(UUID organizationId,
                                                 UUID notificationId,
                                                 UUID actorUserId,
                                                 String note) {
        organizationService.get(organizationId);
        Notification notification = requireDeadLetter(organizationId, notificationId);
        AppUser actor = userService.get(actorUserId);
        notification.setDeadLetterResolutionStatus(DeadLetterResolutionStatus.RESOLVED);
        notification.setDeadLetterResolutionReasonCode(DeadLetterResolutionReasonCode.OTHER);
        notification.setDeadLetterResolutionNote(trimNote(note));
        notification.setDeadLetterResolvedAt(Instant.now());
        notification.setDeadLetterResolvedByUser(actor);
        notification = notificationRepository.save(notification);
        auditService.record(organizationId,
                "NOTIFICATION_DEAD_LETTER_RESOLVED",
                "notification",
                notification.getId().toString(),
                notification.getDeadLetterResolutionNote() != null
                        ? notification.getDeadLetterResolutionNote()
                        : "Dead-letter notification resolved");
        return NotificationSummary.from(notification);
    }

    @Transactional
    public NotificationSummary retryDeadLetter(UUID organizationId,
                                               UUID notificationId,
                                               UUID actorUserId,
                                               String recipientEmail,
                                               String note) {
        organizationService.get(organizationId);
        Notification notification = requireDeadLetter(organizationId, notificationId);
        String normalizedRecipientEmail = normalizeRecipientEmail(recipientEmail);
        if (normalizedRecipientEmail != null) {
            notification.setRecipientEmail(normalizedRecipientEmail);
        }
        resetForRequeue(notification);
        notification = notificationRepository.save(notification);
        auditService.recordForUser(
                actorUserId,
                organizationId,
                "NOTIFICATION_DEAD_LETTER_RETRIED",
                "notification",
                notification.getId().toString(),
                buildRetryAuditDetails(notification.resolvedRecipientEmail(), trimNote(note)));
        return NotificationSummary.from(notification);
    }

    @Transactional
    public NotificationSummary resolveDeadLetterNoResend(UUID organizationId,
                                                         UUID notificationId,
                                                         UUID actorUserId,
                                                         DeadLetterResolutionReasonCode reasonCode,
                                                         String note) {
        organizationService.get(organizationId);
        Notification notification = requireDeadLetter(organizationId, notificationId);
        if (reasonCode == null) {
            throw new IllegalArgumentException("Dead-letter no-resend reason is required");
        }
        AppUser actor = userService.get(actorUserId);
        notification.setDeadLetterResolutionStatus(DeadLetterResolutionStatus.RESOLVED);
        notification.setDeadLetterResolutionReasonCode(reasonCode);
        notification.setDeadLetterResolutionNote(trimNote(note));
        notification.setDeadLetterResolvedAt(Instant.now());
        notification.setDeadLetterResolvedByUser(actor);
        notification = notificationRepository.save(notification);
        auditService.recordForUser(
                actorUserId,
                organizationId,
                "NOTIFICATION_DEAD_LETTER_NO_RESEND",
                "notification",
                notification.getId().toString(),
                reasonCode + (notification.getDeadLetterResolutionNote() != null
                        ? ": " + notification.getDeadLetterResolutionNote()
                        : ""));
        return NotificationSummary.from(notification);
    }

    @Transactional
    public NotificationRequeueResult requeueFailedNotifications(UUID organizationId) {
        organizationService.get(organizationId);
        List<NotificationSummary> requeued = notificationRepository
                .findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, NotificationStatus.FAILED)
                .stream()
                .map(notification -> {
                    resetForRequeue(notification);
                    return notificationRepository.save(notification);
                })
                .map(notification -> {
                    auditService.record(organizationId,
                            "NOTIFICATION_REQUEUED",
                            "notification",
                            notification.getId().toString(),
                            "Failed notification requeued for delivery");
                    return NotificationSummary.from(notification);
                })
                .toList();
        return new NotificationRequeueResult(requeued.size(), requeued);
    }

    @Transactional(readOnly = true)
    public List<NotificationSummary> listForUser(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationSummary::from)
                .toList();
    }

    @Transactional
    public NotificationSummary sendAuthNotification(AppUser user,
                                                    NotificationCategory category,
                                                    NotificationChannel channel,
                                                    String message,
                                                    String referenceType,
                                                    String referenceId) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setCategory(category);
        notification.setChannel(channel);
        notification.setStatus(channel == NotificationChannel.IN_APP ? NotificationStatus.SENT : NotificationStatus.PENDING);
        notification.setDeliveryState(channel == NotificationChannel.IN_APP
                ? NotificationDeliveryState.DELIVERED
                : NotificationDeliveryState.PENDING);
        notification.setMessage(message);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        notification.setRecipientEmail(user.getEmail());
        notification.setScheduledFor(Instant.now());
        notification.setAttemptCount(0);
        notification.setLastFailureCode(null);
        notification.setDeadLetterResolutionStatus(null);
        notification.setDeadLetterResolutionReasonCode(null);
        notification.setDeadLetterResolutionNote(null);
        notification.setDeadLetterResolvedAt(null);
        notification.setDeadLetterResolvedByUser(null);
        if (channel == NotificationChannel.IN_APP) {
            notification.setSentAt(Instant.now());
        }
        notification = notificationRepository.save(notification);
        auditService.record(null,
                channel == NotificationChannel.IN_APP ? "AUTH_NOTIFICATION_SENT" : "AUTH_NOTIFICATION_QUEUED",
                "notification",
                notification.getId().toString(),
                message);
        return NotificationSummary.from(notification);
    }

    private boolean needsReminder(WorkflowTask task, LocalDate today) {
        if (task.getDueDate() == null) {
            return task.getPriority() == WorkflowTaskPriority.HIGH || task.getPriority() == WorkflowTaskPriority.CRITICAL;
        }
        return !task.getDueDate().isAfter(today)
                || task.getPriority() == WorkflowTaskPriority.HIGH
                || task.getPriority() == WorkflowTaskPriority.CRITICAL;
    }

    private NotificationSummary createReminder(WorkflowTask task, LocalDate today) {
        Notification notification = new Notification();
        notification.setOrganization(task.getOrganization());
        notification.setWorkflowTask(task);
        notification.setCategory(NotificationCategory.WORKFLOW);
        notification.setUser(task.getAssignedToUser());
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setStatus(NotificationStatus.SENT);
        notification.setDeliveryState(NotificationDeliveryState.DELIVERED);
        notification.setScheduledFor(Instant.now());
        notification.setSentAt(Instant.now());
        notification.setMessage(buildMessage(task, today));
        notification = notificationRepository.save(notification);
        auditService.record(task.getOrganization().getId(), "TASK_REMINDER_SENT", "notification",
                notification.getId().toString(), notification.getMessage());
        return NotificationSummary.from(notification);
    }

    private String buildMessage(WorkflowTask task, LocalDate today) {
        String urgency = task.getDueDate() != null && task.getDueDate().isBefore(today) ? "Overdue" : "Attention needed";
        return urgency + ": " + task.getTitle();
    }

    private void resetForRequeue(Notification notification) {
        notification.setStatus(NotificationStatus.PENDING);
        notification.setDeliveryState(NotificationDeliveryState.PENDING);
        notification.setScheduledFor(Instant.now());
        notification.setSentAt(null);
        notification.setLastAttemptedAt(null);
        notification.setLastError(null);
        notification.setLastFailureCode(null);
        notification.setProviderName(null);
        notification.setProviderMessageId(null);
        notification.setAttemptCount(0);
        notification.setDeadLetterResolutionStatus(null);
        notification.setDeadLetterResolutionReasonCode(null);
        notification.setDeadLetterResolutionNote(null);
        notification.setDeadLetterResolvedAt(null);
        notification.setDeadLetterResolvedByUser(null);
    }

    private boolean isDeadLetter(Notification notification) {
        return notification.getStatus() == NotificationStatus.FAILED
                && notification.getChannel() == NotificationChannel.EMAIL
                && notification.getDeliveryState() != NotificationDeliveryState.BOUNCED
                && notification.getDeliveryState() != NotificationDeliveryState.COMPLAINED
                && notification.getLastError() != null;
    }

    private Notification requireDeadLetter(UUID organizationId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndOrganizationId(notificationId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification: " + notificationId));
        if (!isUnresolvedDeadLetter(notification)) {
            throw new IllegalArgumentException("Notification is not an open dead letter");
        }
        return notification;
    }

    private boolean isUnresolvedDeadLetter(Notification notification) {
        return isDeadLetter(notification)
                && notification.getDeadLetterResolutionStatus() != DeadLetterResolutionStatus.RESOLVED;
    }

    private DeadLetterQueueItem toQueueItem(Notification notification) {
        String providerName = notification.getProviderName() != null ? notification.getProviderName() : "sendgrid";
        NotificationSuppressionSummary suppression = suppressionService
                .activeSuppression(notification.resolvedRecipientEmail(), providerName)
                .orElse(null);

        if (notification.getDeadLetterResolutionStatus() == DeadLetterResolutionStatus.RESOLVED) {
            return new DeadLetterQueueItem(
                    NotificationSummary.from(notification),
                    DeadLetterRecommendedAction.NONE,
                    suppression != null,
                    suppression,
                    "Resolved recently");
        }
        if (notification.getDeadLetterResolutionStatus() == DeadLetterResolutionStatus.ACKNOWLEDGED) {
            return new DeadLetterQueueItem(
                    NotificationSummary.from(notification),
                    DeadLetterRecommendedAction.REVIEW_ACKNOWLEDGED,
                    suppression != null,
                    suppression,
                    "Acknowledged and awaiting operator follow-up");
        }
        if (suppression != null || "RECIPIENT_SUPPRESSED".equals(notification.getLastFailureCode())) {
            return new DeadLetterQueueItem(
                    NotificationSummary.from(notification),
                    DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY,
                    true,
                    suppression,
                    "Recipient is currently suppressed by the provider");
        }
        return new DeadLetterQueueItem(
                NotificationSummary.from(notification),
                DeadLetterRecommendedAction.RETRY_DELIVERY,
                false,
                null,
                "Delivery can be retried after reviewing the destination");
    }

    private String trimNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
    }

    private String normalizeRecipientEmail(String recipientEmail) {
        if (recipientEmail == null) {
            return null;
        }
        String normalized = recipientEmail.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.contains("@") || normalized.startsWith("@") || normalized.endsWith("@")) {
            throw new IllegalArgumentException("A valid recipient email is required");
        }
        return normalized;
    }

    private String buildRetryAuditDetails(String recipientEmail, String note) {
        String details = "Dead-letter notification requeued for " + recipientEmail;
        return note != null ? details + " (" + note + ")" : details;
    }
}
