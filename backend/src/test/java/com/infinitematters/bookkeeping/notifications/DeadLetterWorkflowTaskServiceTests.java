package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskPriority;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskRepository;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskStatus;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskType;
import com.infinitematters.bookkeeping.users.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterWorkflowTaskServiceTests {

    @Mock
    private NotificationService notificationService;

    @Mock
    private WorkflowTaskRepository workflowTaskRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationRepository notificationRepository;

    private DeadLetterWorkflowTaskService service;

    @BeforeEach
    void setUp() {
        service = new DeadLetterWorkflowTaskService(
                notificationService,
                notificationRepository,
                workflowTaskRepository,
                organizationService,
                auditService,
                2,
                2,
                0.25,
                24,
                48);
    }

    @Test
    void createsSupportTasksForRetryUnsuppressAndStaleAcknowledgedDeadLetters() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);

        Notification retryNotification = notification(UUID.randomUUID(), organization, "retry@example.test");
        Notification unsuppressNotification = notification(UUID.randomUUID(), organization, "suppressed@example.test");
        Notification acknowledgedNotification = notification(UUID.randomUUID(), organization, "ack@example.test");

        DeadLetterQueueSummary queue = new DeadLetterQueueSummary(
                List.of(queueItem(retryNotification, DeadLetterRecommendedAction.RETRY_DELIVERY)),
                List.of(queueItem(unsuppressNotification, DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY)),
                List.of(queueItem(acknowledgedNotification,
                        DeadLetterRecommendedAction.REVIEW_ACKNOWLEDGED,
                        DeadLetterResolutionStatus.ACKNOWLEDGED,
                        Instant.now().minusSeconds(3 * 86400L))),
                List.of());

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(notificationService.deadLetterQueue(organizationId)).thenReturn(queue);
        when(notificationService.requireNotification(retryNotification.getId())).thenReturn(retryNotification);
        when(notificationService.requireNotification(unsuppressNotification.getId())).thenReturn(unsuppressNotification);
        when(notificationService.requireNotification(acknowledgedNotification.getId())).thenReturn(acknowledgedNotification);
        when(workflowTaskRepository.findByNotificationIdAndTaskTypeAndStatus(any(), eq(WorkflowTaskType.DEAD_LETTER_SUPPORT), eq(WorkflowTaskStatus.OPEN)))
                .thenReturn(Optional.empty());
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId, WorkflowTaskType.DEAD_LETTER_SUPPORT, WorkflowTaskStatus.OPEN))
                .thenReturn(List.of());
        when(workflowTaskRepository.save(any(WorkflowTask.class))).thenAnswer(invocation -> {
            WorkflowTask task = invocation.getArgument(0);
            if (task.getId() == null) {
                setId(task, UUID.randomUUID());
            }
            return task;
        });

        DeadLetterTaskRunResult result = service.syncOrganization(organizationId);

        assertThat(result.createdCount()).isEqualTo(3);
        assertThat(result.closedCount()).isZero();

        ArgumentCaptor<WorkflowTask> taskCaptor = ArgumentCaptor.forClass(WorkflowTask.class);
        verify(workflowTaskRepository, times(3)).save(taskCaptor.capture());
        List<WorkflowTask> savedTasks = taskCaptor.getAllValues();
        assertThat(savedTasks)
                .extracting(WorkflowTask::getTaskType)
                .containsOnly(WorkflowTaskType.DEAD_LETTER_SUPPORT);
        assertThat(savedTasks)
                .filteredOn(task -> task.getNotification().getId().equals(retryNotification.getId()))
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.getPriority()).isEqualTo(WorkflowTaskPriority.HIGH);
                    assertThat(task.getTitle()).contains("Retry failed notification delivery");
                    assertThat(task.getDueDate()).isEqualTo(LocalDate.now());
                });
        assertThat(savedTasks)
                .filteredOn(task -> task.getNotification().getId().equals(unsuppressNotification.getId()))
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.getPriority()).isEqualTo(WorkflowTaskPriority.CRITICAL);
                    assertThat(task.getDescription()).contains("UNSUPPRESS_AND_RETRY");
                });
        assertThat(savedTasks)
                .filteredOn(task -> task.getNotification().getId().equals(acknowledgedNotification.getId()))
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.getPriority()).isEqualTo(WorkflowTaskPriority.HIGH);
                    assertThat(task.getDueDate()).isEqualTo(LocalDate.now().plusDays(1));
                });
        verify(auditService, times(3)).record(eq(organizationId), eq("DEAD_LETTER_SUPPORT_TASK_CREATED"), eq("workflow_task"), any(), any());
    }

    @Test
    void refreshesExistingTaskAndClosesObsoleteSupportTask() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);

        Notification retryNotification = notification(UUID.randomUUID(), organization, "retry@example.test");
        Notification obsoleteNotification = notification(UUID.randomUUID(), organization, "old@example.test");

        WorkflowTask existingTask = new WorkflowTask();
        setId(existingTask, UUID.randomUUID());
        existingTask.setOrganization(organization);
        existingTask.setNotification(retryNotification);
        existingTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT);
        existingTask.setStatus(WorkflowTaskStatus.OPEN);
        existingTask.setPriority(WorkflowTaskPriority.MEDIUM);

        WorkflowTask obsoleteTask = new WorkflowTask();
        setId(obsoleteTask, UUID.randomUUID());
        obsoleteTask.setOrganization(organization);
        obsoleteTask.setNotification(obsoleteNotification);
        obsoleteTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT);
        obsoleteTask.setStatus(WorkflowTaskStatus.OPEN);
        obsoleteTask.setPriority(WorkflowTaskPriority.HIGH);

        DeadLetterQueueSummary queue = new DeadLetterQueueSummary(
                List.of(queueItem(retryNotification, DeadLetterRecommendedAction.RETRY_DELIVERY)),
                List.of(),
                List.of(),
                List.of());

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(notificationService.deadLetterQueue(organizationId)).thenReturn(queue);
        when(workflowTaskRepository.findByNotificationIdAndTaskTypeAndStatus(
                retryNotification.getId(), WorkflowTaskType.DEAD_LETTER_SUPPORT, WorkflowTaskStatus.OPEN))
                .thenReturn(Optional.of(existingTask));
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId, WorkflowTaskType.DEAD_LETTER_SUPPORT, WorkflowTaskStatus.OPEN))
                .thenReturn(List.of(existingTask, obsoleteTask));
        when(workflowTaskRepository.save(any(WorkflowTask.class))).thenAnswer(invocation -> {
            WorkflowTask task = invocation.getArgument(0);
            if (task.getId() == null) {
                setId(task, UUID.randomUUID());
            }
            return task;
        });

        DeadLetterTaskRunResult result = service.syncOrganization(organizationId);

        assertThat(result.createdCount()).isZero();
        assertThat(result.closedCount()).isEqualTo(1);
        assertThat(existingTask.getPriority()).isEqualTo(WorkflowTaskPriority.HIGH);
        assertThat(existingTask.getTitle()).contains("Retry failed notification delivery");
        assertThat(existingTask.getStatus()).isEqualTo(WorkflowTaskStatus.OPEN);
        assertThat(obsoleteTask.getStatus()).isEqualTo(WorkflowTaskStatus.COMPLETED);
        assertThat(obsoleteTask.getResolutionComment())
                .isEqualTo("Closed automatically because dead-letter follow-up is no longer required");
        assertThat(obsoleteTask.getResolvedAt()).isNotNull();
        verify(auditService).record(
                eq(organizationId),
                eq("DEAD_LETTER_SUPPORT_TASK_CLOSED"),
                eq("workflow_task"),
                eq(obsoleteTask.getId().toString()),
                any());
    }

    @Test
    void summarizesDeadLetterSupportTaskOwnershipAndAging() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Organization organization = organization(organizationId);

        Notification assignedNotification = notification(UUID.randomUUID(), organization, "assigned@example.test");
        Notification unassignedNotification = notification(UUID.randomUUID(), organization, "unassigned@example.test");
        Notification assignedEscalation = escalationNotification(UUID.randomUUID(), organization, assignedNotification.getId(), "assigned@example.test");

        WorkflowTask assignedTask = new WorkflowTask();
        setId(assignedTask, UUID.randomUUID());
        assignedTask.setOrganization(organization);
        assignedTask.setNotification(assignedNotification);
        assignedTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT);
        assignedTask.setStatus(WorkflowTaskStatus.OPEN);
        assignedTask.setPriority(WorkflowTaskPriority.CRITICAL);
        assignedTask.setTitle("Unsuppress and retry failed notification");
        assignedTask.setDescription("Recipient is suppressed");
        assignedTask.setDueDate(LocalDate.now().minusDays(1));
        setCreatedAt(assignedTask, Instant.now().minusSeconds(4 * 86400L));

        AppUser assignee = new AppUser();
        setId(assignee, userId);
        assignee.setFullName("Ops Owner");
        assignedTask.setAssignedToUser(assignee);

        WorkflowTask unassignedTask = new WorkflowTask();
        setId(unassignedTask, UUID.randomUUID());
        unassignedTask.setOrganization(organization);
        unassignedTask.setNotification(unassignedNotification);
        unassignedTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT);
        unassignedTask.setStatus(WorkflowTaskStatus.OPEN);
        unassignedTask.setPriority(WorkflowTaskPriority.HIGH);
        unassignedTask.setTitle("Retry failed notification delivery");
        unassignedTask.setDescription("Review recipient");
        unassignedTask.setDueDate(LocalDate.now().plusDays(1));
        setCreatedAt(unassignedTask, Instant.now().minusSeconds(86400L));

        WorkflowTask resolvedTask = new WorkflowTask();
        setId(resolvedTask, UUID.randomUUID());
        resolvedTask.setOrganization(organization);
        resolvedTask.setNotification(notification(UUID.randomUUID(), organization, "resolved@example.test"));
        resolvedTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT);
        resolvedTask.setStatus(WorkflowTaskStatus.COMPLETED);
        resolvedTask.setPriority(WorkflowTaskPriority.HIGH);
        resolvedTask.setTitle("Resolved dead-letter support task");
        resolvedTask.setDescription("Resolved after escalation");
        setCreatedAt(resolvedTask, Instant.now().minusSeconds(5 * 86400L));
        setUpdatedAt(resolvedTask, Instant.now().minusSeconds(1800));
        resolvedTask.setResolvedAt(Instant.now().minusSeconds(1800));

        setUpdatedAt(assignedTask, Instant.now().minusSeconds(1800));
        setUpdatedAt(unassignedTask, Instant.now().minusSeconds(7200));

        DeadLetterQueueSummary queue = new DeadLetterQueueSummary(
                List.of(queueItem(unassignedNotification, DeadLetterRecommendedAction.RETRY_DELIVERY)),
                List.of(queueItem(assignedNotification, DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY)),
                List.of(),
                List.of());

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(notificationService.deadLetterQueue(organizationId)).thenReturn(queue);
        setWorkflowTask(assignedEscalation, assignedTask);
        Notification resolvedEscalation = escalationNotification(UUID.randomUUID(), organization, resolvedTask.getNotification().getId(), "resolved@example.test");
        setWorkflowTask(resolvedEscalation, resolvedTask);
        when(notificationRepository.findByOrganizationIdAndReferenceTypeOrderByCreatedAtDesc(
                organizationId, "dead_letter_support_escalation"))
                .thenReturn(List.of(assignedEscalation, resolvedEscalation));
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId, WorkflowTaskType.DEAD_LETTER_SUPPORT, WorkflowTaskStatus.OPEN))
                .thenReturn(List.of(assignedTask, unassignedTask));
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeOrderByCreatedAtAsc(
                organizationId, WorkflowTaskType.DEAD_LETTER_SUPPORT))
                .thenReturn(List.of(assignedTask, unassignedTask, resolvedTask));

        DeadLetterSupportTaskOperationsSummary summary = service.operationsSummary(organizationId);

        assertThat(summary.openCount()).isEqualTo(2);
        assertThat(summary.unassignedCount()).isEqualTo(1);
        assertThat(summary.overdueCount()).isEqualTo(1);
        assertThat(summary.staleCount()).isEqualTo(1);
        assertThat(summary.escalatedCount()).isEqualTo(1);
        assertThat(summary.ignoredEscalationCount()).isEqualTo(1);
        assertThat(summary.assignedAfterEscalationCount()).isEqualTo(1);
        assertThat(summary.resolvedAfterEscalationCount()).isEqualTo(1);
        assertThat(summary.oldestTasks()).hasSize(2);
        assertThat(summary.oldestTasks().get(0).recommendedAction()).isEqualTo(DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY);
        assertThat(summary.oldestTasks().get(0).assignedToUserId()).isEqualTo(userId);
        assertThat(summary.oldestTasks().get(0).stale()).isTrue();
        assertThat(summary.oldestTasks().get(0).overdue()).isTrue();
        assertThat(summary.oldestTasks().get(0).ignoredEscalation()).isTrue();
        assertThat(summary.oldestTasks().get(0).assignedAfterEscalation()).isTrue();
        assertThat(summary.oldestTasks().get(0).resolvedAfterEscalation()).isFalse();
        assertThat(summary.oldestTasks().get(0).escalationCount()).isEqualTo(1);
        assertThat(summary.oldestTasks().get(0).lastEscalatedAt()).isNotNull();
        assertThat(summary.oldestTasks().get(1).recommendedAction()).isEqualTo(DeadLetterRecommendedAction.RETRY_DELIVERY);
        assertThat(summary.oldestTasks().get(1).assignedToUserId()).isNull();
        assertThat(summary.oldestTasks().get(1).ignoredEscalation()).isFalse();
    }

    @Test
    void summarizesDeadLetterSupportEffectivenessByWeek() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);

        WorkflowTask ignoredTask = new WorkflowTask();
        setId(ignoredTask, UUID.randomUUID());
        ignoredTask.setOrganization(organization);
        ignoredTask.setNotification(notification(UUID.randomUUID(), organization, "ignored@example.test"));
        ignoredTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT);
        ignoredTask.setStatus(WorkflowTaskStatus.OPEN);
        ignoredTask.setPriority(WorkflowTaskPriority.HIGH);
        ignoredTask.setDueDate(LocalDate.now().minusDays(1));
        setCreatedAt(ignoredTask, Instant.now().minusSeconds(7 * 86400L));
        setUpdatedAt(ignoredTask, Instant.now().minusSeconds(7 * 86400L));

        WorkflowTask assignedTask = new WorkflowTask();
        setId(assignedTask, UUID.randomUUID());
        assignedTask.setOrganization(organization);
        assignedTask.setNotification(notification(UUID.randomUUID(), organization, "assigned@example.test"));
        assignedTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT);
        assignedTask.setStatus(WorkflowTaskStatus.OPEN);
        assignedTask.setPriority(WorkflowTaskPriority.HIGH);
        assignedTask.setDueDate(LocalDate.now().plusDays(1));
        setCreatedAt(assignedTask, Instant.now().minusSeconds(4 * 86400L));

        AppUser assignee = new AppUser();
        setId(assignee, UUID.randomUUID());
        assignee.setFullName("Ops Owner");
        assignedTask.setAssignedToUser(assignee);

        WorkflowTask resolvedTask = new WorkflowTask();
        setId(resolvedTask, UUID.randomUUID());
        resolvedTask.setOrganization(organization);
        resolvedTask.setNotification(notification(UUID.randomUUID(), organization, "resolved@example.test"));
        resolvedTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT);
        resolvedTask.setStatus(WorkflowTaskStatus.COMPLETED);
        resolvedTask.setPriority(WorkflowTaskPriority.HIGH);
        setCreatedAt(resolvedTask, Instant.now().minusSeconds(10 * 86400L));

        LocalDate currentWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant currentWeekEscalationAt = currentWeekStart.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant priorWeekEscalationAt = currentWeekStart.minusWeeks(1).plusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC);

        setUpdatedAt(assignedTask, currentWeekEscalationAt.plusSeconds(3600));
        setUpdatedAt(resolvedTask, priorWeekEscalationAt.plusSeconds(3600));
        resolvedTask.setResolvedAt(priorWeekEscalationAt.plusSeconds(3600));

        Notification ignoredEscalation = escalationNotification(UUID.randomUUID(), organization, ignoredTask.getNotification().getId(), "ignored@example.test");
        Notification assignedEscalation = escalationNotification(UUID.randomUUID(), organization, assignedTask.getNotification().getId(), "assigned@example.test");
        Notification resolvedEscalation = escalationNotification(UUID.randomUUID(), organization, resolvedTask.getNotification().getId(), "resolved@example.test");
        setWorkflowTask(ignoredEscalation, ignoredTask, currentWeekEscalationAt);
        setWorkflowTask(assignedEscalation, assignedTask, currentWeekEscalationAt.plusSeconds(60));
        setWorkflowTask(resolvedEscalation, resolvedTask, priorWeekEscalationAt);

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(notificationRepository.findByOrganizationIdAndReferenceTypeOrderByCreatedAtDesc(
                organizationId, "dead_letter_support_escalation"))
                .thenReturn(List.of(assignedEscalation, ignoredEscalation, resolvedEscalation));
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeOrderByCreatedAtAsc(
                organizationId, WorkflowTaskType.DEAD_LETTER_SUPPORT))
                .thenReturn(List.of(ignoredTask, assignedTask, resolvedTask));

        DeadLetterSupportEffectivenessSummary summary = service.effectivenessSummary(organizationId, 2);

        assertThat(summary.weeks()).isEqualTo(2);
        assertThat(summary.escalatedCount()).isEqualTo(3);
        assertThat(summary.ignoredEscalationCount()).isEqualTo(1);
        assertThat(summary.assignedAfterEscalationCount()).isEqualTo(1);
        assertThat(summary.resolvedAfterEscalationCount()).isEqualTo(1);
        assertThat(summary.buckets()).hasSize(2);
        assertThat(summary.buckets().get(0).weekStart()).isEqualTo(currentWeekStart.minusWeeks(1));
        assertThat(summary.buckets().get(0).escalatedCount()).isEqualTo(1);
        assertThat(summary.buckets().get(0).resolvedAfterEscalationCount()).isEqualTo(1);
        assertThat(summary.buckets().get(1).weekStart()).isEqualTo(currentWeekStart);
        assertThat(summary.buckets().get(1).escalatedCount()).isEqualTo(2);
        assertThat(summary.buckets().get(1).ignoredEscalationCount()).isEqualTo(1);
        assertThat(summary.buckets().get(1).assignedAfterEscalationCount()).isEqualTo(1);
    }

    @Test
    void summarizesDeadLetterSupportPerformanceAgainstThresholds() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);

        WorkflowTask ignoredTask = new WorkflowTask();
        setId(ignoredTask, UUID.randomUUID());
        ignoredTask.setOrganization(organization);
        ignoredTask.setNotification(notification(UUID.randomUUID(), organization, "ignored@example.test"));
        ignoredTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT);
        ignoredTask.setStatus(WorkflowTaskStatus.OPEN);
        ignoredTask.setPriority(WorkflowTaskPriority.HIGH);
        ignoredTask.setDueDate(LocalDate.now().minusDays(1));
        setCreatedAt(ignoredTask, Instant.now().minusSeconds(5 * 86400L));
        setUpdatedAt(ignoredTask, Instant.now().minusSeconds(5 * 86400L));

        WorkflowTask assignedTask = new WorkflowTask();
        setId(assignedTask, UUID.randomUUID());
        assignedTask.setOrganization(organization);
        assignedTask.setNotification(notification(UUID.randomUUID(), organization, "assigned@example.test"));
        assignedTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT);
        assignedTask.setStatus(WorkflowTaskStatus.OPEN);
        assignedTask.setPriority(WorkflowTaskPriority.HIGH);
        assignedTask.setDueDate(LocalDate.now().plusDays(1));
        setCreatedAt(assignedTask, Instant.now().minusSeconds(3 * 86400L));

        AppUser assignee = new AppUser();
        setId(assignee, UUID.randomUUID());
        assignee.setFullName("Ops Owner");
        assignedTask.setAssignedToUser(assignee);

        WorkflowTask resolvedTask = new WorkflowTask();
        setId(resolvedTask, UUID.randomUUID());
        resolvedTask.setOrganization(organization);
        resolvedTask.setNotification(notification(UUID.randomUUID(), organization, "resolved@example.test"));
        resolvedTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT);
        resolvedTask.setStatus(WorkflowTaskStatus.COMPLETED);
        resolvedTask.setPriority(WorkflowTaskPriority.HIGH);
        setCreatedAt(resolvedTask, Instant.now().minusSeconds(4 * 86400L));

        LocalDate currentWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant ignoredEscalatedAt = currentWeekStart.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant assignedEscalatedAt = currentWeekStart.plusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant resolvedEscalatedAt = currentWeekStart.plusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC);

        setUpdatedAt(assignedTask, assignedEscalatedAt.plusSeconds(30 * 3600L));
        setUpdatedAt(resolvedTask, resolvedEscalatedAt.plusSeconds(12 * 3600L));
        resolvedTask.setResolvedAt(resolvedEscalatedAt.plusSeconds(72 * 3600L));

        Notification ignoredEscalation = escalationNotification(UUID.randomUUID(), organization, ignoredTask.getNotification().getId(), "ignored@example.test");
        Notification assignedEscalation = escalationNotification(UUID.randomUUID(), organization, assignedTask.getNotification().getId(), "assigned@example.test");
        Notification resolvedEscalation = escalationNotification(UUID.randomUUID(), organization, resolvedTask.getNotification().getId(), "resolved@example.test");
        setWorkflowTask(ignoredEscalation, ignoredTask, ignoredEscalatedAt);
        setWorkflowTask(assignedEscalation, assignedTask, assignedEscalatedAt);
        setWorkflowTask(resolvedEscalation, resolvedTask, resolvedEscalatedAt);

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(notificationRepository.findByOrganizationIdAndReferenceTypeOrderByCreatedAtDesc(
                organizationId, "dead_letter_support_escalation"))
                .thenReturn(List.of(ignoredEscalation, assignedEscalation, resolvedEscalation));
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeOrderByCreatedAtAsc(
                organizationId, WorkflowTaskType.DEAD_LETTER_SUPPORT))
                .thenReturn(List.of(ignoredTask, assignedTask, resolvedTask));

        DeadLetterSupportPerformanceSummary summary = service.performanceSummary(organizationId, 6);

        assertThat(summary.weeks()).isEqualTo(6);
        assertThat(summary.escalatedCount()).isEqualTo(3);
        assertThat(summary.ignoredEscalationRate()).isEqualTo(1.0 / 3.0);
        assertThat(summary.averageAssignmentLagHours()).isEqualTo(30.0);
        assertThat(summary.averageResolutionLagHours()).isEqualTo(72.0);
        assertThat(summary.ignoredEscalationRateBreached()).isTrue();
        assertThat(summary.assignmentLagBreached()).isTrue();
        assertThat(summary.resolutionLagBreached()).isTrue();
        assertThat(summary.status()).isEqualTo(DeadLetterSupportPerformanceStatus.AT_RISK);
    }

    private DeadLetterQueueItem queueItem(Notification notification, DeadLetterRecommendedAction action) {
        return queueItem(notification, action, null, null);
    }

    private DeadLetterQueueItem queueItem(Notification notification,
                                          DeadLetterRecommendedAction action,
                                          DeadLetterResolutionStatus resolutionStatus,
                                          Instant resolvedAt) {
        notification.setDeadLetterResolutionStatus(resolutionStatus);
        notification.setDeadLetterResolvedAt(resolvedAt);
        return new DeadLetterQueueItem(
                NotificationSummary.from(notification),
                action,
                false,
                null,
                action.name());
    }

    private Organization organization(UUID organizationId) {
        Organization organization = new Organization();
        setId(organization, organizationId);
        return organization;
    }

    private Notification notification(UUID notificationId, Organization organization, String recipientEmail) {
        Notification notification = new Notification();
        setId(notification, notificationId);
        notification.setOrganization(organization);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setStatus(NotificationStatus.FAILED);
        notification.setDeliveryState(NotificationDeliveryState.FAILED);
        notification.setLastError("Provider rejected delivery");
        notification.setRecipientEmail(recipientEmail);
        return notification;
    }

    private Notification escalationNotification(UUID notificationId,
                                                Organization organization,
                                                UUID referenceId,
                                                String recipientEmail) {
        Notification notification = notification(notificationId, organization, recipientEmail);
        notification.setCategory(NotificationCategory.WORKFLOW);
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setStatus(NotificationStatus.SENT);
        notification.setDeliveryState(NotificationDeliveryState.DELIVERED);
        notification.setReferenceType("dead_letter_support_escalation");
        notification.setReferenceId(referenceId.toString());
        return notification;
    }

    private void setId(Object target, UUID id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void setCreatedAt(WorkflowTask task, Instant createdAt) {
        try {
            Field field = WorkflowTask.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(task, createdAt);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void setUpdatedAt(WorkflowTask task, Instant updatedAt) {
        try {
            Field field = WorkflowTask.class.getDeclaredField("updatedAt");
            field.setAccessible(true);
            field.set(task, updatedAt);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void setWorkflowTask(Notification notification, WorkflowTask task) {
        setWorkflowTask(notification, task, Instant.now().minusSeconds(3600));
    }

    private void setWorkflowTask(Notification notification, WorkflowTask task, Instant createdAt) {
        notification.setWorkflowTask(task);
        try {
            Field field = Notification.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(notification, createdAt);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
