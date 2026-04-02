package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskPriority;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskRepository;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskStatus;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DeadLetterWorkflowTaskService {
    private static final String ESCALATION_REFERENCE_TYPE = "dead_letter_support_escalation";

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final WorkflowTaskRepository workflowTaskRepository;
    private final OrganizationService organizationService;
    private final AuditService auditService;
    private final int acknowledgedTaskAgeDays;
    private final int staleTaskAgeDays;
    private final double ignoredEscalationRateThreshold;
    private final double assignmentLagThresholdHours;
    private final double resolutionLagThresholdHours;

    public DeadLetterWorkflowTaskService(NotificationService notificationService,
                                         NotificationRepository notificationRepository,
                                         WorkflowTaskRepository workflowTaskRepository,
                                         OrganizationService organizationService,
                                         AuditService auditService,
                                         @Value("${bookkeeping.notifications.dead-letter-tasks.acknowledged-age-days:2}")
                                         int acknowledgedTaskAgeDays,
                                         @Value("${bookkeeping.notifications.dead-letter-tasks.stale-age-days:2}")
                                         int staleTaskAgeDays,
                                         @Value("${bookkeeping.notifications.dead-letter-support-performance.ignored-rate-threshold:0.25}")
                                         double ignoredEscalationRateThreshold,
                                         @Value("${bookkeeping.notifications.dead-letter-support-performance.assignment-lag-threshold-hours:24}")
                                         double assignmentLagThresholdHours,
                                         @Value("${bookkeeping.notifications.dead-letter-support-performance.resolution-lag-threshold-hours:48}")
                                         double resolutionLagThresholdHours) {
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.workflowTaskRepository = workflowTaskRepository;
        this.organizationService = organizationService;
        this.auditService = auditService;
        this.acknowledgedTaskAgeDays = acknowledgedTaskAgeDays;
        this.staleTaskAgeDays = staleTaskAgeDays;
        this.ignoredEscalationRateThreshold = ignoredEscalationRateThreshold;
        this.assignmentLagThresholdHours = assignmentLagThresholdHours;
        this.resolutionLagThresholdHours = resolutionLagThresholdHours;
    }

    @Transactional
    public DeadLetterTaskRunResult syncOrganization(UUID organizationId) {
        var organization = organizationService.get(organizationId);
        DeadLetterQueueSummary queue = notificationService.deadLetterQueue(organizationId);
        Set<UUID> requiredNotificationIds = new HashSet<>();
        int createdCount = 0;

        for (DeadLetterQueueItem item : queue.needsRetry()) {
            requiredNotificationIds.add(item.notification().id());
            createdCount += createOrRefreshTask(
                    organizationId,
                    organization,
                    item,
                    "Retry failed notification delivery",
                    "Delivery can be retried after reviewing the recipient and provider failure details.",
                    LocalDate.now(),
                    WorkflowTaskPriority.HIGH);
        }

        for (DeadLetterQueueItem item : queue.needsUnsuppress()) {
            requiredNotificationIds.add(item.notification().id());
            createdCount += createOrRefreshTask(
                    organizationId,
                    organization,
                    item,
                    "Unsuppress and retry failed notification",
                    "Recipient is suppressed and must be unsuppressed before retrying delivery.",
                    LocalDate.now(),
                    WorkflowTaskPriority.CRITICAL);
        }

        for (DeadLetterQueueItem item : queue.acknowledged()) {
            if (isStaleAcknowledged(item)) {
                requiredNotificationIds.add(item.notification().id());
                createdCount += createOrRefreshTask(
                        organizationId,
                        organization,
                        item,
                        "Follow up on acknowledged dead letter",
                        "Acknowledged dead letter still needs operator follow-up.",
                        LocalDate.now().plusDays(1),
                        WorkflowTaskPriority.HIGH);
            }
        }

        int closedCount = closeObsoleteTasks(organizationId, requiredNotificationIds);
        return new DeadLetterTaskRunResult(createdCount, closedCount);
    }

    @Transactional(readOnly = true)
    public DeadLetterSupportTaskOperationsSummary operationsSummary(UUID organizationId) {
        organizationService.get(organizationId);
        List<DeadLetterSupportTaskSummary> openTasks = openTaskSummaries(organizationId);
        List<DeadLetterSupportTaskSummary> allTasks = taskSummaries(organizationId, false);

        long unassignedCount = openTasks.stream()
                .filter(task -> task.assignedToUserId() == null)
                .count();
        long overdueCount = openTasks.stream()
                .filter(DeadLetterSupportTaskSummary::overdue)
                .count();
        long staleCount = openTasks.stream()
                .filter(DeadLetterSupportTaskSummary::stale)
                .count();
        long escalatedCount = openTasks.stream()
                .filter(task -> task.escalationCount() > 0)
                .count();
        long ignoredEscalationCount = openTasks.stream()
                .filter(DeadLetterSupportTaskSummary::ignoredEscalation)
                .count();
        long assignedAfterEscalationCount = allTasks.stream()
                .filter(DeadLetterSupportTaskSummary::assignedAfterEscalation)
                .count();
        long resolvedAfterEscalationCount = allTasks.stream()
                .filter(DeadLetterSupportTaskSummary::resolvedAfterEscalation)
                .count();

        return new DeadLetterSupportTaskOperationsSummary(
                openTasks.size(),
                unassignedCount,
                overdueCount,
                staleCount,
                escalatedCount,
                ignoredEscalationCount,
                assignedAfterEscalationCount,
                resolvedAfterEscalationCount,
                openTasks.stream().limit(5).toList());
    }

    @Transactional(readOnly = true)
    public List<DeadLetterSupportTaskSummary> openTaskSummaries(UUID organizationId) {
        return taskSummaries(organizationId, true);
    }

    @Transactional(readOnly = true)
    public DeadLetterSupportPerformanceSummary performanceSummary(UUID organizationId, int weeks) {
        organizationService.get(organizationId);
        int normalizedWeeks = Math.max(1, Math.min(weeks, 12));
        LocalDate today = LocalDate.now();
        LocalDate currentWeekStart = weekStart(today);
        LocalDate fromWeekStart = currentWeekStart.minusWeeks(normalizedWeeks - 1L);
        LocalDate toWeekEnd = currentWeekStart.plusDays(6);

        List<WorkflowTask> tasks = workflowTaskRepository.findByOrganizationIdAndTaskTypeOrderByCreatedAtAsc(
                organizationId,
                WorkflowTaskType.DEAD_LETTER_SUPPORT);
        Map<UUID, List<Notification>> escalationNotificationsByTaskId = escalationNotificationsByTaskId(organizationId);

        long escalatedCount = 0;
        long ignoredEscalationCount = 0;
        long assignmentLagCount = 0;
        long resolutionLagCount = 0;
        double totalAssignmentLagHours = 0;
        double totalResolutionLagHours = 0;

        for (WorkflowTask task : tasks) {
            for (Notification escalation : escalationNotificationsByTaskId.getOrDefault(task.getId(), List.of())) {
                Instant escalatedAt = escalation.getCreatedAt();
                if (escalatedAt == null) {
                    continue;
                }
                LocalDate escalationWeek = weekStart(escalatedAt.atZone(ZoneOffset.UTC).toLocalDate());
                if (escalationWeek.isBefore(fromWeekStart) || escalationWeek.isAfter(currentWeekStart)) {
                    continue;
                }

                escalatedCount++;
                boolean assignedAfterEscalation = task.getAssignedToUser() != null
                        && task.getUpdatedAt() != null
                        && task.getUpdatedAt().isAfter(escalatedAt);
                boolean resolvedAfterEscalation = task.getResolvedAt() != null
                        && task.getResolvedAt().isAfter(escalatedAt);
                boolean ignoredEscalation = !assignedAfterEscalation
                        && !resolvedAfterEscalation
                        && isOpenAndIgnored(task, today);

                if (ignoredEscalation) {
                    ignoredEscalationCount++;
                }
                if (assignedAfterEscalation) {
                    totalAssignmentLagHours += lagHours(escalatedAt, task.getUpdatedAt());
                    assignmentLagCount++;
                }
                if (resolvedAfterEscalation) {
                    totalResolutionLagHours += lagHours(escalatedAt, task.getResolvedAt());
                    resolutionLagCount++;
                }
            }
        }

        double ignoredEscalationRate = escalatedCount > 0
                ? (double) ignoredEscalationCount / escalatedCount
                : 0.0;
        Double averageAssignmentLagHours = assignmentLagCount > 0
                ? totalAssignmentLagHours / assignmentLagCount
                : null;
        Double averageResolutionLagHours = resolutionLagCount > 0
                ? totalResolutionLagHours / resolutionLagCount
                : null;

        boolean ignoredRateBreached = escalatedCount > 0 && ignoredEscalationRate > ignoredEscalationRateThreshold;
        boolean assignmentLagBreached = averageAssignmentLagHours != null
                && averageAssignmentLagHours > assignmentLagThresholdHours;
        boolean resolutionLagBreached = averageResolutionLagHours != null
                && averageResolutionLagHours > resolutionLagThresholdHours;

        return new DeadLetterSupportPerformanceSummary(
                fromWeekStart,
                toWeekEnd,
                normalizedWeeks,
                escalatedCount,
                ignoredEscalationRate,
                averageAssignmentLagHours,
                averageResolutionLagHours,
                ignoredRateBreached,
                assignmentLagBreached,
                resolutionLagBreached,
                ignoredRateBreached || assignmentLagBreached || resolutionLagBreached
                        ? DeadLetterSupportPerformanceStatus.AT_RISK
                        : DeadLetterSupportPerformanceStatus.ON_TRACK);
    }

    @Transactional(readOnly = true)
    public DeadLetterSupportEffectivenessSummary effectivenessSummary(UUID organizationId, int weeks) {
        organizationService.get(organizationId);
        int normalizedWeeks = Math.max(1, Math.min(weeks, 12));
        LocalDate today = LocalDate.now();
        LocalDate currentWeekStart = weekStart(today);
        LocalDate fromWeekStart = currentWeekStart.minusWeeks(normalizedWeeks - 1L);
        LocalDate toWeekEnd = currentWeekStart.plusDays(6);

        List<WorkflowTask> tasks = workflowTaskRepository.findByOrganizationIdAndTaskTypeOrderByCreatedAtAsc(
                organizationId,
                WorkflowTaskType.DEAD_LETTER_SUPPORT);
        Map<UUID, List<Notification>> escalationNotificationsByTaskId = escalationNotificationsByTaskId(organizationId);
        Map<LocalDate, EffectivenessCounts> buckets = new LinkedHashMap<>();
        for (int index = 0; index < normalizedWeeks; index++) {
            buckets.put(fromWeekStart.plusWeeks(index), new EffectivenessCounts());
        }

        for (WorkflowTask task : tasks) {
            List<Notification> escalations = escalationNotificationsByTaskId.getOrDefault(task.getId(), List.of());
            for (Notification escalation : escalations) {
                Instant escalatedAt = escalation.getCreatedAt();
                if (escalatedAt == null) {
                    continue;
                }
                LocalDate escalationWeek = weekStart(escalatedAt.atZone(ZoneOffset.UTC).toLocalDate());
                EffectivenessCounts counts = buckets.get(escalationWeek);
                if (counts == null) {
                    continue;
                }

                counts.escalatedCount++;
                boolean assignedAfterEscalation = task.getAssignedToUser() != null
                        && task.getUpdatedAt() != null
                        && task.getUpdatedAt().isAfter(escalatedAt);
                boolean resolvedAfterEscalation = task.getResolvedAt() != null
                        && task.getResolvedAt().isAfter(escalatedAt);
                boolean ignoredEscalation = !assignedAfterEscalation
                        && !resolvedAfterEscalation
                        && isOpenAndIgnored(task, today);

                if (assignedAfterEscalation) {
                    counts.assignedAfterEscalationCount++;
                }
                if (resolvedAfterEscalation) {
                    counts.resolvedAfterEscalationCount++;
                }
                if (ignoredEscalation) {
                    counts.ignoredEscalationCount++;
                }
            }
        }

        List<DeadLetterSupportEffectivenessBucket> bucketSummaries = buckets.entrySet()
                .stream()
                .map(entry -> new DeadLetterSupportEffectivenessBucket(
                        entry.getKey(),
                        entry.getKey().plusDays(6),
                        entry.getValue().escalatedCount,
                        entry.getValue().ignoredEscalationCount,
                        entry.getValue().assignedAfterEscalationCount,
                        entry.getValue().resolvedAfterEscalationCount))
                .toList();

        long totalEscalated = bucketSummaries.stream().mapToLong(DeadLetterSupportEffectivenessBucket::escalatedCount).sum();
        long totalIgnored = bucketSummaries.stream().mapToLong(DeadLetterSupportEffectivenessBucket::ignoredEscalationCount).sum();
        long totalAssigned = bucketSummaries.stream().mapToLong(DeadLetterSupportEffectivenessBucket::assignedAfterEscalationCount).sum();
        long totalResolved = bucketSummaries.stream().mapToLong(DeadLetterSupportEffectivenessBucket::resolvedAfterEscalationCount).sum();

        return new DeadLetterSupportEffectivenessSummary(
                fromWeekStart,
                toWeekEnd,
                normalizedWeeks,
                totalEscalated,
                totalIgnored,
                totalAssigned,
                totalResolved,
                bucketSummaries);
    }

    @Transactional(readOnly = true)
    public List<DeadLetterSupportTaskSummary> taskSummaries(UUID organizationId, boolean openOnly) {
        organizationService.get(organizationId);
        LocalDate today = LocalDate.now();
        Map<UUID, DeadLetterQueueItem> queueItemsByNotificationId = queueItemsByNotificationId(
                notificationService.deadLetterQueue(organizationId));
        Map<UUID, List<Notification>> escalationNotificationsByTaskId = escalationNotificationsByTaskId(organizationId);

        List<WorkflowTask> tasks = openOnly
                ? workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                        organizationId,
                        WorkflowTaskType.DEAD_LETTER_SUPPORT,
                        WorkflowTaskStatus.OPEN)
                : workflowTaskRepository.findByOrganizationIdAndTaskTypeOrderByCreatedAtAsc(
                        organizationId,
                        WorkflowTaskType.DEAD_LETTER_SUPPORT);

        return tasks
                .stream()
                .map(task -> toSummary(task, queueItemsByNotificationId.get(task.getNotification() != null
                        ? task.getNotification().getId()
                        : null), escalationNotificationsByTaskId.getOrDefault(task.getId(), List.of()), today))
                .sorted(Comparator.comparingLong(DeadLetterSupportTaskSummary::ageDays).reversed())
                .toList();
    }

    private int createOrRefreshTask(UUID organizationId,
                                    com.infinitematters.bookkeeping.organization.Organization organization,
                                    DeadLetterQueueItem item,
                                    String titlePrefix,
                                    String descriptionPrefix,
                                    LocalDate dueDate,
                                    WorkflowTaskPriority priority) {
        UUID notificationId = item.notification().id();
        WorkflowTask task = workflowTaskRepository
                .findByNotificationIdAndTaskTypeAndStatus(notificationId, WorkflowTaskType.DEAD_LETTER_SUPPORT, WorkflowTaskStatus.OPEN)
                .orElse(null);
        if (task == null) {
            task = new WorkflowTask();
            task.setOrganization(organization);
            task.setNotification(requireNotification(notificationId));
            task.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT);
            task.setStatus(WorkflowTaskStatus.OPEN);
            task.setPriority(priority);
            task.setDueDate(dueDate);
            task.setTitle(titlePrefix + ": " + safeRecipient(item.notification().recipientEmail()));
            task.setDescription(descriptionPrefix + " Recommended action: " + item.recommendedAction()
                    + ". Notification " + notificationId + ".");
            workflowTaskRepository.save(task);
            auditService.record(organizationId,
                    "DEAD_LETTER_SUPPORT_TASK_CREATED",
                    "workflow_task",
                    task.getId().toString(),
                    "Created support task for notification " + notificationId);
            return 1;
        }
        task.setPriority(priority);
        task.setDueDate(dueDate);
        task.setTitle(titlePrefix + ": " + safeRecipient(item.notification().recipientEmail()));
        task.setDescription(descriptionPrefix + " Recommended action: " + item.recommendedAction()
                + ". Notification " + notificationId + ".");
        workflowTaskRepository.save(task);
        return 0;
    }

    private int closeObsoleteTasks(UUID organizationId, Set<UUID> requiredNotificationIds) {
        List<WorkflowTask> openTasks = workflowTaskRepository
                .findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                        organizationId,
                        WorkflowTaskType.DEAD_LETTER_SUPPORT,
                        WorkflowTaskStatus.OPEN);
        int closedCount = 0;
        for (WorkflowTask task : openTasks) {
            UUID notificationId = task.getNotification() != null ? task.getNotification().getId() : null;
            if (notificationId == null || !requiredNotificationIds.contains(notificationId)) {
                task.setStatus(WorkflowTaskStatus.COMPLETED);
                task.setResolutionComment("Closed automatically because dead-letter follow-up is no longer required");
                task.setResolvedAt(Instant.now());
                workflowTaskRepository.save(task);
                auditService.record(organizationId,
                        "DEAD_LETTER_SUPPORT_TASK_CLOSED",
                        "workflow_task",
                        task.getId().toString(),
                        "Closed support task for notification " + notificationId);
                closedCount++;
            }
        }
        return closedCount;
    }

    private Notification requireNotification(UUID notificationId) {
        return notificationService.requireNotification(notificationId);
    }

    private boolean isStaleAcknowledged(DeadLetterQueueItem item) {
        Instant acknowledgedAt = item.notification().deadLetterResolvedAt();
        if (acknowledgedAt == null) {
            return true;
        }
        return acknowledgedAt.isBefore(Instant.now().minusSeconds(acknowledgedTaskAgeDays * 86400L));
    }

    private Map<UUID, DeadLetterQueueItem> queueItemsByNotificationId(DeadLetterQueueSummary queue) {
        Map<UUID, DeadLetterQueueItem> itemsByNotificationId = new HashMap<>();
        queue.needsRetry().forEach(item -> itemsByNotificationId.put(item.notification().id(), item));
        queue.needsUnsuppress().forEach(item -> itemsByNotificationId.put(item.notification().id(), item));
        queue.acknowledged().forEach(item -> itemsByNotificationId.put(item.notification().id(), item));
        queue.recentlyResolved().forEach(item -> itemsByNotificationId.put(item.notification().id(), item));
        return itemsByNotificationId;
    }

    private Map<UUID, List<Notification>> escalationNotificationsByTaskId(UUID organizationId) {
        return notificationRepository.findByOrganizationIdAndReferenceTypeOrderByCreatedAtDesc(
                        organizationId,
                        ESCALATION_REFERENCE_TYPE)
                .stream()
                .filter(notification -> notification.getWorkflowTask() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        notification -> notification.getWorkflowTask().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
    }

    private DeadLetterSupportTaskSummary toSummary(WorkflowTask task,
                                                   DeadLetterQueueItem queueItem,
                                                   List<Notification> escalationNotifications,
                                                   LocalDate today) {
        LocalDate createdDate = task.getCreatedAt() != null
                ? task.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate()
                : today;
        long ageDays = ChronoUnit.DAYS.between(createdDate, today);
        boolean overdue = task.getDueDate() != null && task.getDueDate().isBefore(today);
        boolean stale = ageDays >= staleTaskAgeDays;
        Instant lastEscalatedAt = escalationNotifications.stream()
                .map(Notification::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        int escalationCount = escalationNotifications.size();
        boolean ignoredEscalation = escalationCount > 0 && (overdue || stale);
        boolean assignedAfterEscalation = lastEscalatedAt != null
                && task.getAssignedToUser() != null
                && task.getUpdatedAt() != null
                && task.getUpdatedAt().isAfter(lastEscalatedAt);
        boolean resolvedAfterEscalation = lastEscalatedAt != null
                && task.getResolvedAt() != null
                && task.getResolvedAt().isAfter(lastEscalatedAt);
        return new DeadLetterSupportTaskSummary(
                task.getId(),
                task.getNotification() != null ? task.getNotification().getId() : null,
                queueItem != null ? queueItem.recommendedAction() : DeadLetterRecommendedAction.NONE,
                queueItem != null ? queueItem.recommendationReason() : "Support follow-up remains open",
                task.getPriority().name(),
                overdue,
                stale,
                ignoredEscalation,
                assignedAfterEscalation,
                resolvedAfterEscalation,
                ageDays,
                escalationCount,
                lastEscalatedAt,
                task.getDueDate(),
                task.getAssignedToUser() != null ? task.getAssignedToUser().getId() : null,
                task.getAssignedToUser() != null ? task.getAssignedToUser().getFullName() : null,
                task.getNotification() != null ? task.getNotification().resolvedRecipientEmail() : null,
                task.getTitle(),
                task.getDescription());
    }

    private boolean isOpenAndIgnored(WorkflowTask task, LocalDate today) {
        if (task.getStatus() != WorkflowTaskStatus.OPEN) {
            return false;
        }
        boolean overdue = task.getDueDate() != null && task.getDueDate().isBefore(today);
        LocalDate createdDate = task.getCreatedAt() != null
                ? task.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate()
                : today;
        long ageDays = ChronoUnit.DAYS.between(createdDate, today);
        boolean stale = ageDays >= staleTaskAgeDays;
        return overdue || stale;
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private double lagHours(Instant from, Instant to) {
        return ChronoUnit.MINUTES.between(from, to) / 60.0;
    }

    private String safeRecipient(String recipientEmail) {
        return recipientEmail != null && !recipientEmail.isBlank() ? recipientEmail : "notification";
    }

    private static final class EffectivenessCounts {
        private long escalatedCount;
        private long ignoredEscalationCount;
        private long assignedAfterEscalationCount;
        private long resolvedAfterEscalationCount;
    }
}
