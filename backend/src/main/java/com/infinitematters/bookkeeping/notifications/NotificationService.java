package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.workflows.ReviewQueueService;
import com.infinitematters.bookkeeping.workflows.ReviewTaskSummary;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskPriority;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskRepository;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.UUID;

@Service
public class NotificationService {
    private static final java.time.Duration CLOSE_CONTROL_ESCALATION_DEFAULT_REMINDER_COOLDOWN = java.time.Duration.ofHours(12);
    private static final java.time.Duration CLOSE_CONTROL_ESCALATION_REVISIT_REMINDER_COOLDOWN = java.time.Duration.ofHours(24);
    private static final String PERFORMANCE_REFERENCE_TYPE = "dead_letter_support_performance";
    private static final String PERFORMANCE_ESCALATION_REFERENCE_TYPE = "dead_letter_support_performance_escalation";
    private static final String CLOSE_CONTROL_ESCALATION_REFERENCE_TYPE = "close_control_follow_up_escalation";
    private static final String CLOSE_CONTROL_REFERENCE_TYPE = "close_control_follow_up";
    private static final String CLOSE_CONTROL_ESCALATION_ACKNOWLEDGED_EVENT = "CLOSE_CONTROL_ESCALATION_ACKNOWLEDGED";
    private static final String CLOSE_CONTROL_ESCALATION_RESOLVED_EVENT = "CLOSE_CONTROL_ESCALATION_RESOLVED";

    private final NotificationRepository notificationRepository;
    private final WorkflowTaskRepository workflowTaskRepository;
    private final OrganizationService organizationService;
    private final NotificationSuppressionService suppressionService;
    private final AuditService auditService;
    private final UserService userService;
    private final ReviewQueueService reviewQueueService;

    public NotificationService(NotificationRepository notificationRepository,
                               WorkflowTaskRepository workflowTaskRepository,
                               OrganizationService organizationService,
                               NotificationSuppressionService suppressionService,
                               AuditService auditService,
                               UserService userService,
                               ReviewQueueService reviewQueueService) {
        this.notificationRepository = notificationRepository;
        this.workflowTaskRepository = workflowTaskRepository;
        this.organizationService = organizationService;
        this.suppressionService = suppressionService;
        this.auditService = auditService;
        this.userService = userService;
        this.reviewQueueService = reviewQueueService;
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

        List<NotificationSummary> closeControlCreated = reviewQueueService.listCloseControlAttentionTasks(organizationId)
                .stream()
                .filter(task -> needsCloseControlReminder(organizationId, task, today))
                .filter(task -> !notificationRepository.existsByOrganizationIdAndReferenceTypeAndReferenceIdAndStatusAndScheduledForAfter(
                        organizationId,
                        CLOSE_CONTROL_REFERENCE_TYPE,
                        task.taskId().toString(),
                        NotificationStatus.SENT,
                        dayStart))
                .map(task -> createCloseControlReminder(organizationId, task, today))
                .toList();

        List<NotificationSummary> notifications = Stream.concat(created.stream(), closeControlCreated.stream())
                .toList();
        return new ReminderRunResult(notifications.size(), notifications);
    }

    @Transactional(readOnly = true)
    public List<NotificationSummary> listForOrganization(UUID organizationId) {
        organizationService.get(organizationId);
        return notificationRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .filter(this::isVisibleWorkflowNotification)
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.Optional<NotificationSummary> latestForReference(UUID organizationId,
                                                                      String referenceType,
                                                                      String referenceId) {
        organizationService.get(organizationId);
        return notificationRepository
                .findTopByOrganizationIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                        organizationId, referenceType, referenceId)
                .map(this::toSummary);
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
        List<NotificationSummary> deliveryAttentionNotifications = notificationRepository
                .findTop10ByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                        organizationId,
                        List.of(NotificationStatus.PENDING, NotificationStatus.FAILED))
                .stream()
                .filter(this::isVisibleWorkflowNotification)
                .filter(notification -> notification.getStatus() != NotificationStatus.FAILED
                        || notification.getDeadLetterResolutionStatus() != DeadLetterResolutionStatus.RESOLVED)
                .map(NotificationSummary::from)
                .toList();
        List<NotificationSummary> performanceAttentionNotifications = notificationRepository
                .findByOrganizationIdAndReferenceTypeOrderByCreatedAtDesc(organizationId, PERFORMANCE_REFERENCE_TYPE)
                .stream()
                .filter(this::isActiveUnacknowledgedPerformanceNotification)
                .collect(java.util.stream.Collectors.toMap(
                        notification -> notification.getWorkflowTask().getId(),
                        Function.identity(),
                        (left, right) -> left.getCreatedAt().isAfter(right.getCreatedAt()) ? left : right))
                .values()
                .stream()
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .map(NotificationSummary::from)
                .toList();
        java.util.Set<String> activeCloseControlTaskIds = reviewQueueService.listCloseControlAttentionTasks(organizationId).stream()
                .map(task -> task.taskId().toString())
                .collect(java.util.stream.Collectors.toSet());
        List<NotificationSummary> closeControlEscalationNotifications = notificationRepository
                .findByOrganizationIdAndReferenceTypeOrderByCreatedAtDesc(organizationId, CLOSE_CONTROL_ESCALATION_REFERENCE_TYPE)
                .stream()
                .filter(notification -> activeCloseControlTaskIds.contains(notification.getReferenceId()))
                .filter(this::isActiveCloseControlEscalation)
                .collect(java.util.stream.Collectors.toMap(
                        Notification::getReferenceId,
                        Function.identity(),
                        (left, right) -> left.getCreatedAt().isAfter(right.getCreatedAt()) ? left : right))
                .values()
                .stream()
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .map(this::toSummary)
                .toList();
        List<NotificationSummary> attentionNotifications = Stream.concat(
                        Stream.concat(
                                closeControlEscalationNotifications.stream(),
                                performanceAttentionNotifications.stream()),
                        deliveryAttentionNotifications.stream())
                .limit(10)
                .toList();
        long retryingCount = deliveryAttentionNotifications.stream()
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
                .filter(this::isVisibleWorkflowNotification)
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public NotificationSummary acknowledgeCloseControlEscalation(UUID organizationId,
                                                                 UUID notificationId,
                                                                 UUID actorUserId,
                                                                 String note,
                                                                 CloseControlDisposition disposition,
                                                                 LocalDate nextTouchOn) {
        Notification notification = requireCloseControlEscalation(organizationId, notificationId);
        String trimmedNote = trimNote(note);
        CloseControlDisposition normalizedDisposition = normalizeCloseControlDisposition(disposition);
        notification.setCloseControlDisposition(normalizedDisposition);
        notification.setCloseControlNextTouchOn(normalizeCloseControlNextTouchOn(
                organizationId,
                notification,
                normalizedDisposition,
                nextTouchOn));
        notification = notificationRepository.save(notification);
        auditService.recordForUser(
                actorUserId,
                organizationId,
                CLOSE_CONTROL_ESCALATION_ACKNOWLEDGED_EVENT,
                "workflow_task",
                notification.getReferenceId(),
                trimmedNote != null ? trimmedNote : "Escalated close-control review acknowledged");
        return toSummary(notification);
    }

    @Transactional
    public NotificationSummary resolveCloseControlEscalation(UUID organizationId,
                                                             UUID notificationId,
                                                             UUID actorUserId,
                                                             String note,
                                                             CloseControlDisposition disposition,
                                                             LocalDate nextTouchOn) {
        Notification notification = requireCloseControlEscalation(organizationId, notificationId);
        String trimmedNote = trimNote(note);
        CloseControlDisposition normalizedDisposition = normalizeCloseControlDisposition(disposition);
        notification.setCloseControlDisposition(normalizedDisposition);
        notification.setCloseControlNextTouchOn(normalizeCloseControlNextTouchOn(
                organizationId,
                notification,
                normalizedDisposition,
                nextTouchOn));
        notification = notificationRepository.save(notification);
        auditService.recordForUser(
                actorUserId,
                organizationId,
                CLOSE_CONTROL_ESCALATION_RESOLVED_EVENT,
                "workflow_task",
                notification.getReferenceId(),
                trimmedNote != null ? trimmedNote : "Escalated close-control review resolved");
        return toSummary(notification);
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

    @Transactional
    public NotificationSummary sendOrganizationNotification(UUID organizationId,
                                                            String recipientEmail,
                                                            NotificationCategory category,
                                                            NotificationChannel channel,
                                                            String message,
                                                            String referenceType,
                                                            String referenceId) {
        Notification notification = new Notification();
        notification.setOrganization(organizationService.get(organizationId));
        notification.setUser(userService.findByEmail(recipientEmail).orElse(null));
        notification.setCategory(category);
        notification.setChannel(channel);
        notification.setStatus(channel == NotificationChannel.IN_APP ? NotificationStatus.SENT : NotificationStatus.PENDING);
        notification.setDeliveryState(channel == NotificationChannel.IN_APP
                ? NotificationDeliveryState.DELIVERED
                : NotificationDeliveryState.PENDING);
        notification.setMessage(message);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        notification.setRecipientEmail(recipientEmail);
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
        auditService.record(organizationId,
                channel == NotificationChannel.IN_APP
                        ? "WORKSPACE_NOTIFICATION_SENT"
                        : "WORKSPACE_NOTIFICATION_QUEUED",
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

    private boolean needsCloseControlReminder(UUID organizationId, ReviewTaskSummary task, LocalDate today) {
        if (task.assignedToUserId() == null) {
            return false;
        }
        if (hasRecentAcknowledgedCloseControlEscalation(organizationId, task.taskId())) {
            return false;
        }
        if ("CLOSE_ATTESTATION_FOLLOW_UP".equals(task.taskType())) {
            return task.acknowledgedAt() != null
                    && task.resolvedAt() == null
                    && closeControlReminderDue(task, today);
        }
        return "FORCE_CLOSE_REVIEW".equals(task.taskType())
                && task.resolvedAt() == null
                && closeControlReminderDue(task, today);
    }

    private boolean hasRecentAcknowledgedCloseControlEscalation(UUID organizationId, UUID taskId) {
        java.util.Optional<Notification> latestEscalation = notificationRepository
                .findTopByOrganizationIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                        organizationId,
                        CLOSE_CONTROL_ESCALATION_REFERENCE_TYPE,
                        taskId.toString());
        java.time.Duration cooldownWindow = latestEscalation
                .map(Notification::getCloseControlDisposition)
                .map(this::closeControlReminderCooldownFor)
                .orElse(CLOSE_CONTROL_ESCALATION_DEFAULT_REMINDER_COOLDOWN);
        Instant cooldownStart = Instant.now().minus(cooldownWindow);
        return auditService.listForOrganizationByEventTypeAndEntity(
                        organizationId,
                        CLOSE_CONTROL_ESCALATION_ACKNOWLEDGED_EVENT,
                        taskId.toString())
                .stream()
                .anyMatch(event -> !event.createdAt().isBefore(cooldownStart));
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

    private NotificationSummary createCloseControlReminder(UUID organizationId,
                                                           ReviewTaskSummary task,
                                                           LocalDate today) {
        CloseControlDisposition disposition = latestCloseControlDisposition(organizationId, task.taskId())
                .orElse(defaultDisposition(task));
        Notification notification = new Notification();
        notification.setOrganization(organizationService.get(organizationId));
        notification.setCategory(NotificationCategory.WORKFLOW);
        notification.setUser(userService.get(task.assignedToUserId()));
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setStatus(NotificationStatus.SENT);
        notification.setDeliveryState(NotificationDeliveryState.DELIVERED);
        notification.setReferenceType(CLOSE_CONTROL_REFERENCE_TYPE);
        notification.setReferenceId(task.taskId().toString());
        notification.setScheduledFor(Instant.now());
        notification.setSentAt(Instant.now());
        notification.setCloseControlDisposition(disposition);
        notification.setCloseControlNextTouchOn(task.dueDate());
        notification.setMessage(buildCloseControlReminderMessage(task, today, disposition));
        notification = notificationRepository.save(notification);
        auditService.record(organizationId,
                "CLOSE_CONTROL_REMINDER_SENT",
                "notification",
                notification.getId().toString(),
                notification.getMessage());
        return NotificationSummary.from(notification);
    }

    private String buildMessage(WorkflowTask task, LocalDate today) {
        String urgency = task.getDueDate() != null && task.getDueDate().isBefore(today) ? "Overdue" : "Attention needed";
        return urgency + ": " + task.getTitle();
    }

    private String buildCloseControlReminderMessage(ReviewTaskSummary task,
                                                    LocalDate today,
                                                    CloseControlDisposition disposition) {
        String urgency = task.dueDate() != null && task.dueDate().isBefore(today) ? "Overdue" : "Attention needed";
        String month = extractTaskMonth(task);
        if (disposition == CloseControlDisposition.REVISIT_TOMORROW) {
            return urgency + ": revisit the close-control follow-up for " + month + scheduledNextTouchSuffix(task.dueDate());
        }
        if (disposition == CloseControlDisposition.OVERRIDE_DOCS_IN_PROGRESS) {
            return urgency + ": override documentation still needs owner follow-through for " + month;
        }
        return urgency + ": final attestation confirmation is still missing for " + month;
    }

    private boolean closeControlReminderDue(ReviewTaskSummary task, LocalDate today) {
        if (task.dueDate() == null) {
            return true;
        }
        if (!task.dueDate().isAfter(today)) {
            return true;
        }
        return "HIGH".equals(task.priority()) || "CRITICAL".equals(task.priority());
    }

    private CloseControlDisposition defaultDisposition(ReviewTaskSummary task) {
        return "FORCE_CLOSE_REVIEW".equals(task.taskType())
                ? CloseControlDisposition.OVERRIDE_DOCS_IN_PROGRESS
                : CloseControlDisposition.WAITING_ON_APPROVER;
    }

    private java.util.Optional<CloseControlDisposition> latestCloseControlDisposition(UUID organizationId, UUID taskId) {
        return notificationRepository
                .findTopByOrganizationIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                        organizationId,
                        CLOSE_CONTROL_ESCALATION_REFERENCE_TYPE,
                        taskId.toString())
                .map(Notification::getCloseControlDisposition);
    }

    private String extractTaskMonth(ReviewTaskSummary task) {
        String actionPath = task.actionPath();
        if (actionPath != null) {
            int monthIndex = actionPath.indexOf("month=");
            if (monthIndex >= 0) {
                return actionPath.substring(monthIndex + "month=".length());
            }
        }
        return task.title();
    }

    private boolean isActiveCloseControlEscalation(Notification notification) {
        if (!CLOSE_CONTROL_ESCALATION_REFERENCE_TYPE.equals(notification.getReferenceType())
                || notification.getReferenceId() == null
                || notification.getOrganization() == null) {
            return false;
        }
        return latestCloseControlEscalationAction(
                notification.getOrganization().getId(),
                CLOSE_CONTROL_ESCALATION_RESOLVED_EVENT,
                notification.getReferenceId(),
                notification.getCreatedAt()).isEmpty();
    }

    private NotificationSummary toSummary(Notification notification) {
        if (!CLOSE_CONTROL_ESCALATION_REFERENCE_TYPE.equals(notification.getReferenceType())
                || notification.getReferenceId() == null
                || notification.getOrganization() == null) {
            return NotificationSummary.from(notification);
        }
        UUID organizationId = notification.getOrganization().getId();
        java.util.Optional<com.infinitematters.bookkeeping.audit.AuditEventSummary> acknowledgement =
                latestCloseControlEscalationAction(
                        organizationId,
                        CLOSE_CONTROL_ESCALATION_ACKNOWLEDGED_EVENT,
                        notification.getReferenceId(),
                        notification.getCreatedAt());
        java.util.Optional<com.infinitematters.bookkeeping.audit.AuditEventSummary> resolution =
                latestCloseControlEscalationAction(
                        organizationId,
                        CLOSE_CONTROL_ESCALATION_RESOLVED_EVENT,
                        notification.getReferenceId(),
                        notification.getCreatedAt());
        return NotificationSummary.from(
                notification,
                acknowledgement.map(com.infinitematters.bookkeeping.audit.AuditEventSummary::details).orElse(null),
                acknowledgement.map(com.infinitematters.bookkeeping.audit.AuditEventSummary::createdAt).orElse(null),
                acknowledgement.map(com.infinitematters.bookkeeping.audit.AuditEventSummary::actorUserId).orElse(null),
                notification.getCloseControlDisposition(),
                notification.getCloseControlNextTouchOn(),
                resolution.map(com.infinitematters.bookkeeping.audit.AuditEventSummary::details).orElse(null),
                resolution.map(com.infinitematters.bookkeeping.audit.AuditEventSummary::createdAt).orElse(null),
                resolution.map(com.infinitematters.bookkeeping.audit.AuditEventSummary::actorUserId).orElse(null));
    }

    private CloseControlDisposition normalizeCloseControlDisposition(CloseControlDisposition disposition) {
        return disposition != null ? disposition : CloseControlDisposition.WAITING_ON_APPROVER;
    }

    private LocalDate normalizeCloseControlNextTouchOn(UUID organizationId,
                                                       Notification notification,
                                                       CloseControlDisposition disposition,
                                                       LocalDate nextTouchOn) {
        if (disposition != CloseControlDisposition.REVISIT_TOMORROW) {
            return null;
        }
        LocalDate defaultDate = suggestedCloseControlNextTouchOn(organizationId, notification);
        LocalDate effectiveDate = nextTouchOn != null ? nextTouchOn : defaultDate;
        if (effectiveDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Close-control next touch date cannot be in the past");
        }
        return effectiveDate;
    }

    private LocalDate suggestedCloseControlNextTouchOn(UUID organizationId, Notification notification) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        if (notification.getReferenceId() == null) {
            return tomorrow;
        }
        UUID taskId;
        try {
            taskId = UUID.fromString(notification.getReferenceId());
        } catch (IllegalArgumentException ex) {
            return tomorrow;
        }
        ReviewTaskSummary task = reviewQueueService.listCloseControlAttentionTasks(organizationId)
                .stream()
                .filter(item -> item.taskId().equals(taskId))
                .findFirst()
                .orElse(null);
        if (task == null) {
            return tomorrow;
        }
        if ("FORCE_CLOSE_REVIEW".equals(task.taskType()) && !task.overdue()) {
            LocalDate ownerFollowUpWindow = LocalDate.now().plusDays(2);
            return task.dueDate() != null && task.dueDate().isAfter(ownerFollowUpWindow)
                    ? task.dueDate()
                    : ownerFollowUpWindow;
        }
        if (task.dueDate() != null && task.dueDate().isAfter(tomorrow)) {
            return task.dueDate();
        }
        return tomorrow;
    }

    private java.time.Duration closeControlReminderCooldownFor(CloseControlDisposition disposition) {
        if (disposition == CloseControlDisposition.REVISIT_TOMORROW) {
            return CLOSE_CONTROL_ESCALATION_REVISIT_REMINDER_COOLDOWN;
        }
        return CLOSE_CONTROL_ESCALATION_DEFAULT_REMINDER_COOLDOWN;
    }

    private java.util.Optional<com.infinitematters.bookkeeping.audit.AuditEventSummary> latestCloseControlEscalationAction(
            UUID organizationId,
            String eventType,
            String referenceId,
            Instant sourceCreatedAt) {
        return auditService.listForOrganizationByEventTypeAndEntity(organizationId, eventType, referenceId)
                .stream()
                .filter(event -> !event.createdAt().isBefore(sourceCreatedAt))
                .max(Comparator.comparing(com.infinitematters.bookkeeping.audit.AuditEventSummary::createdAt));
    }

    private String scheduledNextTouchSuffix(LocalDate nextTouchOn) {
        return nextTouchOn != null ? " on " + nextTouchOn : " in today's review window";
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

    private boolean isVisibleWorkflowNotification(Notification notification) {
        if (!isPerformanceNotification(notification)) {
            return true;
        }
        WorkflowTask task = notification.getWorkflowTask();
        if (task == null) {
            return true;
        }
        if (task.getStatus() != WorkflowTaskStatus.OPEN) {
            return false;
        }
        if (task.getSnoozedUntil() != null && !task.getSnoozedUntil().isBefore(LocalDate.now())) {
            return !PERFORMANCE_ESCALATION_REFERENCE_TYPE.equals(notification.getReferenceType());
        }
        return !isPerformanceEscalationNotification(notification) || task.getAcknowledgedAt() == null;
    }

    private boolean isActiveUnacknowledgedPerformanceNotification(Notification notification) {
        if (!PERFORMANCE_REFERENCE_TYPE.equals(notification.getReferenceType())) {
            return false;
        }
        WorkflowTask task = notification.getWorkflowTask();
        return task != null
                && task.getStatus() == WorkflowTaskStatus.OPEN
                && (task.getSnoozedUntil() == null || task.getSnoozedUntil().isBefore(LocalDate.now()))
                && task.getAcknowledgedAt() == null;
    }

    private boolean isPerformanceNotification(Notification notification) {
        return PERFORMANCE_REFERENCE_TYPE.equals(notification.getReferenceType())
                || isPerformanceEscalationNotification(notification);
    }

    private boolean isPerformanceEscalationNotification(Notification notification) {
        return PERFORMANCE_ESCALATION_REFERENCE_TYPE.equals(notification.getReferenceType());
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

    private Notification requireCloseControlEscalation(UUID organizationId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndOrganizationId(notificationId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification: " + notificationId));
        if (!CLOSE_CONTROL_ESCALATION_REFERENCE_TYPE.equals(notification.getReferenceType())
                || notification.getReferenceId() == null) {
            throw new IllegalArgumentException("Notification is not a close-control escalation");
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
