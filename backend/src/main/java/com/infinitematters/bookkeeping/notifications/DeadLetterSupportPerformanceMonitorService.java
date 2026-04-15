package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskPriority;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskRepository;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskStatus;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class DeadLetterSupportPerformanceMonitorService {
    private static final String PERFORMANCE_REACTIVATED_EVENT_TYPE = "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED";
    private static final String PERFORMANCE_REFERENCE_TYPE = "dead_letter_support_performance";
    private static final String PERFORMANCE_ESCALATION_REFERENCE_TYPE = "dead_letter_support_performance_escalation";

    private final DeadLetterWorkflowTaskService deadLetterWorkflowTaskService;
    private final WorkflowTaskRepository workflowTaskRepository;
    private final NotificationRepository notificationRepository;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final AuditService auditService;
    private final long escalationAgeHours;

    public DeadLetterSupportPerformanceMonitorService(DeadLetterWorkflowTaskService deadLetterWorkflowTaskService,
                                                      WorkflowTaskRepository workflowTaskRepository,
                                                      NotificationRepository notificationRepository,
                                                      OrganizationService organizationService,
                                                      UserService userService,
                                                      AuditService auditService,
                                                      @Value("${bookkeeping.notifications.dead-letter-support-performance.escalation-age-hours:24}")
                                                      long escalationAgeHours) {
        this.deadLetterWorkflowTaskService = deadLetterWorkflowTaskService;
        this.workflowTaskRepository = workflowTaskRepository;
        this.notificationRepository = notificationRepository;
        this.organizationService = organizationService;
        this.userService = userService;
        this.auditService = auditService;
        this.escalationAgeHours = escalationAgeHours;
    }

    @Transactional
    public DeadLetterSupportPerformanceMonitorRunResult syncOrganization(UUID organizationId) {
        Organization organization = organizationService.get(organizationId);
        DeadLetterSupportPerformanceSummary performance = deadLetterWorkflowTaskService.performanceSummary(organizationId, 6);
        WorkflowTask openTask = workflowTaskRepository
                .findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                        organizationId,
                        WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE,
                        WorkflowTaskStatus.OPEN)
                .stream()
                .findFirst()
                .orElse(null);

        if (openTask != null && isExpiredSnooze(openTask)) {
            reactivateExpiredSnooze(openTask, organizationId);
        }

        if (performance.status() == DeadLetterSupportPerformanceStatus.AT_RISK) {
            if (openTask == null) {
                WorkflowTask created = new WorkflowTask();
                created.setOrganization(organization);
                created.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
                created.setStatus(WorkflowTaskStatus.OPEN);
                created.setPriority(WorkflowTaskPriority.CRITICAL);
                created.setDueDate(LocalDate.now());
                created.setTitle("Dead-letter support performance at risk");
                created.setDescription(buildDescription(performance));
                assignOwnerIfPresent(created, organizationId);
                workflowTaskRepository.save(created);
                createOwnerNotificationIfAssigned(created);
                int escalatedCount = maybeEscalateIgnoredRisk(created, organizationId);
                auditService.record(
                        organizationId,
                        "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_CREATED",
                        "workflow_task",
                        created.getId().toString(),
                        "Created support performance risk task");
                return new DeadLetterSupportPerformanceMonitorRunResult(1, 0, escalatedCount);
            }
            openTask.setPriority(WorkflowTaskPriority.CRITICAL);
            openTask.setDueDate(LocalDate.now());
            openTask.setTitle("Dead-letter support performance at risk");
            openTask.setDescription(buildDescription(performance));
            assignOwnerIfPresent(openTask, organizationId);
            workflowTaskRepository.save(openTask);
            return new DeadLetterSupportPerformanceMonitorRunResult(0, 0, maybeEscalateIgnoredRisk(openTask, organizationId));
        }

        if (openTask != null) {
            openTask.setStatus(WorkflowTaskStatus.COMPLETED);
            openTask.setResolvedAt(Instant.now());
            openTask.setResolutionComment("Closed automatically because dead-letter support performance returned on track");
            workflowTaskRepository.save(openTask);
            auditService.record(
                    organizationId,
                    "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_CLOSED",
                    "workflow_task",
                    openTask.getId().toString(),
                    "Closed support performance risk task");
            return new DeadLetterSupportPerformanceMonitorRunResult(0, 1, 0);
        }
        return new DeadLetterSupportPerformanceMonitorRunResult(0, 0, 0);
    }

    @Transactional
    public List<DeadLetterSupportPerformanceMonitorRunResult> syncAllOrganizations() {
        return organizationService.list().stream()
                .map(organization -> syncOrganization(organization.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DeadLetterSupportPerformanceTaskStatus taskStatus(UUID organizationId) {
        List<WorkflowTask> openTasks = workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId,
                WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE,
                WorkflowTaskStatus.OPEN);
        DeadLetterSupportPerformanceTaskQueueSummary queueSummary = summarize(openTasks);
        return new DeadLetterSupportPerformanceTaskStatus(
                queueSummary.openTaskCount(),
                queueSummary.acknowledgedTaskCount(),
                queueSummary.snoozedTaskCount(),
                queueSummary.ignoredTaskCount(),
                queueSummary.secondaryEscalationCount());
    }

    @Transactional(readOnly = true)
    public List<WorkflowTask> listOpenRiskTasks(UUID organizationId) {
        return listOpenRiskTasks(organizationId, DeadLetterSupportPerformanceTaskFilter.ALL);
    }

    @Transactional(readOnly = true)
    public List<WorkflowTask> listOpenRiskTasks(UUID organizationId, DeadLetterSupportPerformanceTaskFilter filter) {
        organizationService.get(organizationId);
        return workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId,
                WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE,
                WorkflowTaskStatus.OPEN).stream()
                .filter(task -> matchesFilter(task, filter))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowTask> listHighPriorityRiskTasks(UUID organizationId) {
        organizationService.get(organizationId);
        return workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                        organizationId,
                        WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE,
                        WorkflowTaskStatus.OPEN).stream()
                .filter(task -> isIgnored(task) || isReactivatedOverdue(task, organizationId))
                .sorted((left, right) -> {
                    int overdueComparison = Boolean.compare(
                            isReactivatedOverdue(right, organizationId),
                            isReactivatedOverdue(left, organizationId));
                    if (overdueComparison != 0) {
                        return overdueComparison;
                    }
                    int ignoredComparison = Boolean.compare(isIgnored(right), isIgnored(left));
                    if (ignoredComparison != 0) {
                        return ignoredComparison;
                    }
                    return left.getCreatedAt().compareTo(right.getCreatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public DeadLetterSupportPerformanceTaskQueueSummary queueSummary(UUID organizationId) {
        organizationService.get(organizationId);
        List<WorkflowTask> openTasks = workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId,
                WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE,
                WorkflowTaskStatus.OPEN);
        return summarize(openTasks);
    }

    @Transactional
    public WorkflowTask acknowledgeRiskTask(UUID organizationId, UUID taskId, UUID actorUserId, String note) {
        WorkflowTask task = requireOpenRiskTask(organizationId, taskId);
        AppUser actor = userService.get(actorUserId);
        task.setAcknowledgedAt(Instant.now());
        task.setAcknowledgedByUser(actor);
        task.setSnoozedUntil(null);
        if (note != null && !note.isBlank()) {
            task.setResolutionComment(note.trim());
        }
        workflowTaskRepository.save(task);
        auditService.record(
                organizationId,
                "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_ACKNOWLEDGED",
                "workflow_task",
                task.getId().toString(),
                note != null && !note.isBlank() ? note.trim() : "Performance risk task acknowledged");
        return task;
    }

    @Transactional
    public WorkflowTask resolveRiskTask(UUID organizationId, UUID taskId, UUID actorUserId, String note) {
        WorkflowTask task = requireOpenRiskTask(organizationId, taskId);
        AppUser actor = userService.get(actorUserId);
        if (task.getAcknowledgedAt() == null) {
            task.setAcknowledgedAt(Instant.now());
            task.setAcknowledgedByUser(actor);
        }
        task.setStatus(WorkflowTaskStatus.COMPLETED);
        task.setResolvedAt(Instant.now());
        task.setResolvedByUser(actor);
        task.setSnoozedUntil(null);
        if (note != null && !note.isBlank()) {
            task.setResolutionComment(note.trim());
        } else if (task.getResolutionComment() == null || task.getResolutionComment().isBlank()) {
            task.setResolutionComment("Dead-letter support performance risk resolved");
        }
        workflowTaskRepository.save(task);
        auditService.record(
                organizationId,
                "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_RESOLVED",
                "workflow_task",
                task.getId().toString(),
                task.getResolutionComment());
        return task;
    }

    @Transactional
    public WorkflowTask snoozeRiskTask(UUID organizationId, UUID taskId, UUID actorUserId, LocalDate snoozedUntil, String note) {
        if (snoozedUntil == null) {
            throw new IllegalArgumentException("Snoozed-until date is required");
        }
        if (snoozedUntil.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Snoozed-until date must be today or later");
        }
        WorkflowTask task = requireOpenRiskTask(organizationId, taskId);
        AppUser actor = userService.get(actorUserId);
        task.setAcknowledgedAt(Instant.now());
        task.setAcknowledgedByUser(actor);
        task.setSnoozedUntil(snoozedUntil);
        if (note != null && !note.isBlank()) {
            task.setResolutionComment(note.trim());
        }
        workflowTaskRepository.save(task);
        auditService.record(
                organizationId,
                "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_SNOOZED",
                "workflow_task",
                task.getId().toString(),
                buildSnoozeAuditDetails(snoozedUntil, note));
        return task;
    }

    private void assignOwnerIfPresent(WorkflowTask task, UUID organizationId) {
        if (task.getAssignedToUser() != null) {
            return;
        }
        ownerOrAdmin(organizationId).ifPresent(assignee -> {
            task.setAssignedToUser(assignee);
            auditService.record(
                    organizationId,
                    "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_ASSIGNED",
                    "workflow_task",
                    task.getId() != null ? task.getId().toString() : "pending",
                    "Assigned support performance risk task to " + assignee.getEmail());
        });
    }

    private java.util.Optional<AppUser> ownerOrAdmin(UUID organizationId) {
        return userService.membersForOrganizationWithRoles(
                        organizationId,
                        List.of(UserRole.OWNER, UserRole.ADMIN))
                .stream()
                .findFirst();
    }

    private void createOwnerNotificationIfAssigned(WorkflowTask task) {
        AppUser assignee = task.getAssignedToUser();
        if (assignee == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setOrganization(task.getOrganization());
        notification.setWorkflowTask(task);
        notification.setCategory(NotificationCategory.WORKFLOW);
        notification.setUser(assignee);
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setStatus(NotificationStatus.SENT);
        notification.setDeliveryState(NotificationDeliveryState.DELIVERED);
        notification.setReferenceType(PERFORMANCE_REFERENCE_TYPE);
        notification.setReferenceId(task.getId().toString());
        notification.setRecipientEmail(assignee.getEmail());
        notification.setScheduledFor(Instant.now());
        notification.setSentAt(Instant.now());
        notification.setAttemptCount(0);
        notification.setMessage("Dead-letter support performance is at risk. Review the new workflow task '"
                + task.getTitle()
                + "' and take ownership of remediation.");
        notificationRepository.save(notification);
        auditService.record(
                task.getOrganization().getId(),
                "DEAD_LETTER_SUPPORT_PERFORMANCE_NOTIFIED",
                "workflow_task",
                task.getId().toString(),
                "Notified " + assignee.getEmail() + " about support performance risk");
    }

    private int maybeEscalateIgnoredRisk(WorkflowTask task, UUID organizationId) {
        if (task.getAcknowledgedAt() != null || isSnoozed(task)) {
            return 0;
        }
        Notification initialNotification = notificationRepository
                .findByWorkflowTaskIdAndReferenceTypeOrderByCreatedAtAsc(task.getId(), PERFORMANCE_REFERENCE_TYPE)
                .stream()
                .findFirst()
                .orElse(null);
        if (initialNotification == null) {
            return 0;
        }
        if (initialNotification.getCreatedAt().isAfter(Instant.now().minusSeconds(escalationAgeHours * 3600))) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        Instant dayStart = today.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant();
        int createdCount = 0;
        for (AppUser recipient : userService.membersForOrganizationWithRoles(
                organizationId,
                List.of(UserRole.OWNER, UserRole.ADMIN))) {
            if (notificationRepository.existsByWorkflowTaskIdAndUserIdAndReferenceTypeAndStatusAndScheduledForAfter(
                    task.getId(),
                    recipient.getId(),
                    PERFORMANCE_ESCALATION_REFERENCE_TYPE,
                    NotificationStatus.SENT,
                    dayStart)) {
                continue;
            }
            Notification notification = new Notification();
            notification.setOrganization(task.getOrganization());
            notification.setWorkflowTask(task);
            notification.setCategory(NotificationCategory.WORKFLOW);
            notification.setUser(recipient);
            notification.setChannel(NotificationChannel.IN_APP);
            notification.setStatus(NotificationStatus.SENT);
            notification.setDeliveryState(NotificationDeliveryState.DELIVERED);
            notification.setReferenceType(PERFORMANCE_ESCALATION_REFERENCE_TYPE);
            notification.setReferenceId(task.getId().toString());
            notification.setRecipientEmail(recipient.getEmail());
            notification.setScheduledFor(Instant.now());
            notification.setSentAt(Instant.now());
            notification.setAttemptCount(0);
            notification.setMessage("Dead-letter support performance risk is still open and needs executive attention. "
                    + "The workflow task '" + task.getTitle() + "' remains unresolved.");
            notificationRepository.save(notification);
            auditService.record(
                    organizationId,
                    "DEAD_LETTER_SUPPORT_PERFORMANCE_ESCALATED",
                    "workflow_task",
                    task.getId().toString(),
                    "Escalated support performance risk to " + recipient.getEmail());
            createdCount++;
        }
        return createdCount;
    }

    private String buildSnoozeAuditDetails(LocalDate snoozedUntil, String note) {
        String details = "Snoozed support performance risk until " + snoozedUntil;
        if (note != null && !note.isBlank()) {
            return details + " (" + note.trim() + ")";
        }
        return details;
    }

    private DeadLetterSupportPerformanceTaskQueueSummary summarize(List<WorkflowTask> openTasks) {
        long assignedTaskCount = openTasks.stream()
                .filter(task -> task.getAssignedToUser() != null)
                .count();
        long acknowledgedTaskCount = openTasks.stream()
                .filter(task -> task.getAcknowledgedAt() != null)
                .count();
        long snoozedTaskCount = openTasks.stream()
                .filter(this::isSnoozed)
                .count();
        long overdueTaskCount = openTasks.stream()
                .filter(this::isOverdue)
                .count();
        long ignoredTaskCount = openTasks.stream()
                .filter(this::isIgnored)
                .count();
        long reactivatedNeedsAttentionCount = openTasks.stream()
                .filter(task -> isReactivatedNeedingAttention(task, task.getOrganization().getId()))
                .count();
        long reactivatedOverdueCount = openTasks.stream()
                .filter(task -> isReactivatedOverdue(task, task.getOrganization().getId()))
                .count();
        long secondaryEscalationCount = openTasks.stream()
                .mapToLong(this::secondaryEscalationCount)
                .sum();
        return new DeadLetterSupportPerformanceTaskQueueSummary(
                openTasks.size(),
                assignedTaskCount,
                openTasks.size() - assignedTaskCount,
                acknowledgedTaskCount,
                openTasks.size() - acknowledgedTaskCount,
                snoozedTaskCount,
                overdueTaskCount,
                ignoredTaskCount,
                reactivatedNeedsAttentionCount,
                reactivatedOverdueCount,
                secondaryEscalationCount);
    }

    private boolean matchesFilter(WorkflowTask task, DeadLetterSupportPerformanceTaskFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case ACKNOWLEDGED -> task.getAcknowledgedAt() != null;
            case UNACKNOWLEDGED -> task.getAcknowledgedAt() == null;
            case SNOOZED -> isSnoozed(task);
            case ASSIGNED -> task.getAssignedToUser() != null;
            case UNASSIGNED -> task.getAssignedToUser() == null;
            case OVERDUE -> isOverdue(task);
            case IGNORED -> isIgnored(task);
            case REACTIVATED_NEEDS_ATTENTION -> isReactivatedNeedingAttention(task, task.getOrganization().getId());
            case REACTIVATED_OVERDUE -> isReactivatedOverdue(task, task.getOrganization().getId());
        };
    }

    private boolean isOverdue(WorkflowTask task) {
        return task.getDueDate() != null && task.getDueDate().isBefore(LocalDate.now());
    }

    private boolean isIgnored(WorkflowTask task) {
        return task.getAcknowledgedAt() == null && !isSnoozed(task) && secondaryEscalationCount(task) > 0;
    }

    private boolean isReactivatedNeedingAttention(WorkflowTask task, UUID organizationId) {
        return task.getAcknowledgedAt() == null
                && !isSnoozed(task)
                && auditService.hasOrganizationEventForEntity(
                        organizationId,
                        PERFORMANCE_REACTIVATED_EVENT_TYPE,
                        task.getId().toString());
    }

    private boolean isReactivatedOverdue(WorkflowTask task, UUID organizationId) {
        return isReactivatedNeedingAttention(task, organizationId) && isOverdue(task);
    }

    private boolean isSnoozed(WorkflowTask task) {
        return task.getSnoozedUntil() != null && !task.getSnoozedUntil().isBefore(LocalDate.now());
    }

    private boolean isExpiredSnooze(WorkflowTask task) {
        return task.getSnoozedUntil() != null && task.getSnoozedUntil().isBefore(LocalDate.now());
    }

    private long secondaryEscalationCount(WorkflowTask task) {
        return notificationRepository.countByWorkflowTaskIdAndReferenceType(
                task.getId(),
                PERFORMANCE_ESCALATION_REFERENCE_TYPE);
    }

    private void reactivateExpiredSnooze(WorkflowTask task, UUID organizationId) {
        LocalDate expiredOn = task.getSnoozedUntil();
        task.setSnoozedUntil(null);
        task.setAcknowledgedAt(null);
        task.setAcknowledgedByUser(null);
        workflowTaskRepository.save(task);
        createOwnerNotificationIfAssigned(task);
        auditService.record(
                organizationId,
                PERFORMANCE_REACTIVATED_EVENT_TYPE,
                "workflow_task",
                task.getId().toString(),
                "Reactivated support performance risk after snooze expired on " + expiredOn);
    }

    private WorkflowTask requireOpenRiskTask(UUID organizationId, UUID taskId) {
        return workflowTaskRepository.findById(taskId)
                .filter(task -> task.getOrganization().getId().equals(organizationId))
                .filter(task -> task.getTaskType() == WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE)
                .filter(task -> task.getStatus() == WorkflowTaskStatus.OPEN)
                .orElseThrow(() -> new IllegalArgumentException("Open dead-letter support performance task not found: " + taskId));
    }

    private String buildDescription(DeadLetterSupportPerformanceSummary performance) {
        return "Dead-letter support performance is at risk over the last "
                + performance.weeks()
                + " weeks. Ignored escalation rate="
                + Math.round(performance.ignoredEscalationRate() * 100)
                + "%, average assignment lag hours="
                + formatHours(performance.averageAssignmentLagHours())
                + ", average resolution lag hours="
                + formatHours(performance.averageResolutionLagHours())
                + ". Breaches: ignoredRate="
                + performance.ignoredEscalationRateBreached()
                + ", assignmentLag="
                + performance.assignmentLagBreached()
                + ", resolutionLag="
                + performance.resolutionLagBreached()
                + ".";
    }

    private String formatHours(Double value) {
        return value == null ? "n/a" : String.format(java.util.Locale.US, "%.1f", value);
    }
}
