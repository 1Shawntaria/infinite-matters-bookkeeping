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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterSupportPerformanceMonitorServiceTests {

    @Mock
    private DeadLetterWorkflowTaskService deadLetterWorkflowTaskService;

    @Mock
    private WorkflowTaskRepository workflowTaskRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    private DeadLetterSupportPerformanceMonitorService service;

    @BeforeEach
    void setUp() {
        service = new DeadLetterSupportPerformanceMonitorService(
                deadLetterWorkflowTaskService,
                workflowTaskRepository,
                notificationRepository,
                organizationService,
                userService,
                auditService,
                24);
    }

    @Test
    void createsPerformanceRiskTaskWhenSupportIsAtRisk() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        AppUser owner = user(UUID.randomUUID(), "owner@acme.test");
        when(organizationService.get(organizationId)).thenReturn(organization);
        when(deadLetterWorkflowTaskService.performanceSummary(organizationId, 6))
                .thenReturn(new DeadLetterSupportPerformanceSummary(
                        java.time.LocalDate.now().minusWeeks(5),
                        java.time.LocalDate.now().plusDays(2),
                        6,
                        4,
                        0.5,
                        30.0,
                        60.0,
                        true,
                        true,
                        true,
                        DeadLetterSupportPerformanceStatus.AT_RISK));
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId,
                WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE,
                WorkflowTaskStatus.OPEN))
                .thenReturn(List.of());
        when(userService.membersForOrganizationWithRoles(organizationId, List.of(UserRole.OWNER, UserRole.ADMIN)))
                .thenReturn(List.of(owner));
        when(workflowTaskRepository.save(any(WorkflowTask.class))).thenAnswer(invocation -> {
            WorkflowTask task = invocation.getArgument(0);
            if (task.getId() == null) {
                setId(task, UUID.randomUUID());
            }
            return task;
        });
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getId() == null) {
                setId(notification, UUID.randomUUID());
            }
            return notification;
        });

        DeadLetterSupportPerformanceMonitorRunResult result = service.syncOrganization(organizationId);

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.closedCount()).isZero();
        assertThat(result.escalatedCount()).isZero();

        ArgumentCaptor<WorkflowTask> taskCaptor = ArgumentCaptor.forClass(WorkflowTask.class);
        verify(workflowTaskRepository).save(taskCaptor.capture());
        WorkflowTask saved = taskCaptor.getValue();
        assertThat(saved.getTaskType()).isEqualTo(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        assertThat(saved.getPriority()).isEqualTo(WorkflowTaskPriority.CRITICAL);
        assertThat(saved.getStatus()).isEqualTo(WorkflowTaskStatus.OPEN);
        assertThat(saved.getTitle()).contains("performance at risk");
        assertThat(saved.getDescription()).contains("Ignored escalation rate=50%");
        assertThat(saved.getAssignedToUser()).isEqualTo(owner);
        verify(auditService).record(
                eq(organizationId),
                eq("DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_CREATED"),
                eq("workflow_task"),
                any(),
                any());
        verify(auditService).record(
                eq(organizationId),
                eq("DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_ASSIGNED"),
                eq("workflow_task"),
                any(),
                any());
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification notification = notificationCaptor.getValue();
        assertThat(notification.getUser()).isEqualTo(owner);
        assertThat(notification.getWorkflowTask()).isEqualTo(saved);
        assertThat(notification.getReferenceType()).isEqualTo("dead_letter_support_performance");
    }

    @Test
    void closesOpenPerformanceRiskTaskWhenSupportReturnsOnTrack() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        WorkflowTask openTask = new WorkflowTask();
        setId(openTask, UUID.randomUUID());
        openTask.setOrganization(organization);
        openTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        openTask.setStatus(WorkflowTaskStatus.OPEN);
        openTask.setPriority(WorkflowTaskPriority.CRITICAL);

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(deadLetterWorkflowTaskService.performanceSummary(organizationId, 6))
                .thenReturn(new DeadLetterSupportPerformanceSummary(
                        java.time.LocalDate.now().minusWeeks(5),
                        java.time.LocalDate.now().plusDays(2),
                        6,
                        4,
                        0.0,
                        8.0,
                        12.0,
                        false,
                        false,
                        false,
                        DeadLetterSupportPerformanceStatus.ON_TRACK));
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId,
                WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE,
                WorkflowTaskStatus.OPEN))
                .thenReturn(List.of(openTask));

        DeadLetterSupportPerformanceMonitorRunResult result = service.syncOrganization(organizationId);

        assertThat(result.createdCount()).isZero();
        assertThat(result.closedCount()).isEqualTo(1);
        assertThat(result.escalatedCount()).isZero();
        assertThat(openTask.getStatus()).isEqualTo(WorkflowTaskStatus.COMPLETED);
        assertThat(openTask.getResolvedAt()).isNotNull();
        assertThat(openTask.getResolutionComment()).contains("returned on track");
        verify(auditService).record(
                eq(organizationId),
                eq("DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_CLOSED"),
                eq("workflow_task"),
                eq(openTask.getId().toString()),
                any());
    }

    @Test
    void escalatesIgnoredPerformanceRiskTaskAfterThreshold() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        AppUser owner = user(UUID.randomUUID(), "owner@acme.test");
        AppUser admin = user(UUID.randomUUID(), "admin@acme.test");
        WorkflowTask openTask = new WorkflowTask();
        setId(openTask, UUID.randomUUID());
        openTask.setOrganization(organization);
        openTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        openTask.setStatus(WorkflowTaskStatus.OPEN);
        openTask.setPriority(WorkflowTaskPriority.CRITICAL);
        openTask.setTitle("Dead-letter support performance at risk");

        Notification initialNotification = new Notification();
        setId(initialNotification, UUID.randomUUID());
        initialNotification.setOrganization(organization);
        initialNotification.setWorkflowTask(openTask);
        initialNotification.setReferenceType("dead_letter_support_performance");
        setCreatedAt(initialNotification, Instant.now().minusSeconds(26 * 3600));

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(deadLetterWorkflowTaskService.performanceSummary(organizationId, 6))
                .thenReturn(new DeadLetterSupportPerformanceSummary(
                        java.time.LocalDate.now().minusWeeks(5),
                        java.time.LocalDate.now().plusDays(2),
                        6,
                        4,
                        0.5,
                        30.0,
                        60.0,
                        true,
                        true,
                        true,
                        DeadLetterSupportPerformanceStatus.AT_RISK));
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId,
                WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE,
                WorkflowTaskStatus.OPEN))
                .thenReturn(List.of(openTask));
        when(userService.membersForOrganizationWithRoles(organizationId, List.of(UserRole.OWNER, UserRole.ADMIN)))
                .thenReturn(List.of(owner, admin));
        when(notificationRepository.findByWorkflowTaskIdAndReferenceTypeOrderByCreatedAtAsc(
                openTask.getId(),
                "dead_letter_support_performance"))
                .thenReturn(List.of(initialNotification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getId() == null) {
                setId(notification, UUID.randomUUID());
            }
            return notification;
        });

        DeadLetterSupportPerformanceMonitorRunResult result = service.syncOrganization(organizationId);

        assertThat(result.createdCount()).isZero();
        assertThat(result.closedCount()).isZero();
        assertThat(result.escalatedCount()).isEqualTo(2);
        verify(auditService, times(2)).record(
                eq(organizationId),
                eq("DEAD_LETTER_SUPPORT_PERFORMANCE_ESCALATED"),
                eq("workflow_task"),
                eq(openTask.getId().toString()),
                any());
    }

    @Test
    void acknowledgeRiskTaskMarksTaskInProgress() {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        AppUser actor = user(actorUserId, "owner@acme.test");
        WorkflowTask openTask = new WorkflowTask();
        setId(openTask, UUID.randomUUID());
        openTask.setOrganization(organization);
        openTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        openTask.setStatus(WorkflowTaskStatus.OPEN);

        when(userService.get(actorUserId)).thenReturn(actor);
        when(workflowTaskRepository.findById(openTask.getId())).thenReturn(java.util.Optional.of(openTask));

        WorkflowTask acknowledged = service.acknowledgeRiskTask(
                organizationId,
                openTask.getId(),
                actorUserId,
                "Investigating support backlog");

        assertThat(acknowledged.getAcknowledgedByUser()).isEqualTo(actor);
        assertThat(acknowledged.getAcknowledgedAt()).isNotNull();
        assertThat(acknowledged.getStatus()).isEqualTo(WorkflowTaskStatus.OPEN);
        assertThat(acknowledged.getResolutionComment()).isEqualTo("Investigating support backlog");
        verify(auditService).record(
                eq(organizationId),
                eq("DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_ACKNOWLEDGED"),
                eq("workflow_task"),
                eq(openTask.getId().toString()),
                eq("Investigating support backlog"));
    }

    @Test
    void resolveRiskTaskCompletesTaskAndPreservesAcknowledgement() {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        AppUser actor = user(actorUserId, "owner@acme.test");
        WorkflowTask openTask = new WorkflowTask();
        setId(openTask, UUID.randomUUID());
        openTask.setOrganization(organization);
        openTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        openTask.setStatus(WorkflowTaskStatus.OPEN);

        when(userService.get(actorUserId)).thenReturn(actor);
        when(workflowTaskRepository.findById(openTask.getId())).thenReturn(java.util.Optional.of(openTask));

        WorkflowTask resolved = service.resolveRiskTask(
                organizationId,
                openTask.getId(),
                actorUserId,
                "Resolved staffing issue");

        assertThat(resolved.getAcknowledgedByUser()).isEqualTo(actor);
        assertThat(resolved.getAcknowledgedAt()).isNotNull();
        assertThat(resolved.getResolvedByUser()).isEqualTo(actor);
        assertThat(resolved.getResolvedAt()).isNotNull();
        assertThat(resolved.getStatus()).isEqualTo(WorkflowTaskStatus.COMPLETED);
        assertThat(resolved.getResolutionComment()).isEqualTo("Resolved staffing issue");
        verify(auditService).record(
                eq(organizationId),
                eq("DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_RESOLVED"),
                eq("workflow_task"),
                eq(openTask.getId().toString()),
                eq("Resolved staffing issue"));
    }

    @Test
    void snoozeRiskTaskAcknowledgesAndDefersEscalationPressure() {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        AppUser actor = user(actorUserId, "owner@acme.test");
        WorkflowTask openTask = new WorkflowTask();
        setId(openTask, UUID.randomUUID());
        openTask.setOrganization(organization);
        openTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        openTask.setStatus(WorkflowTaskStatus.OPEN);

        when(userService.get(actorUserId)).thenReturn(actor);
        when(workflowTaskRepository.findById(openTask.getId())).thenReturn(java.util.Optional.of(openTask));

        LocalDate snoozedUntil = LocalDate.now().plusDays(3);
        WorkflowTask snoozed = service.snoozeRiskTask(
                organizationId,
                openTask.getId(),
                actorUserId,
                snoozedUntil,
                "Waiting for staffing update");

        assertThat(snoozed.getAcknowledgedByUser()).isEqualTo(actor);
        assertThat(snoozed.getAcknowledgedAt()).isNotNull();
        assertThat(snoozed.getSnoozedUntil()).isEqualTo(snoozedUntil);
        assertThat(snoozed.getResolutionComment()).isEqualTo("Waiting for staffing update");
        verify(auditService).record(
                eq(organizationId),
                eq("DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_SNOOZED"),
                eq("workflow_task"),
                eq(openTask.getId().toString()),
                eq("Snoozed support performance risk until " + snoozedUntil + " (Waiting for staffing update)"));
    }

    @Test
    void syncOrganizationReactivatesExpiredSnoozedRiskTask() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        AppUser owner = user(UUID.randomUUID(), "owner@acme.test");
        WorkflowTask openTask = new WorkflowTask();
        setId(openTask, UUID.randomUUID());
        openTask.setOrganization(organization);
        openTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        openTask.setStatus(WorkflowTaskStatus.OPEN);
        openTask.setPriority(WorkflowTaskPriority.CRITICAL);
        openTask.setTitle("Dead-letter support performance at risk");
        openTask.setAssignedToUser(owner);
        openTask.setAcknowledgedAt(Instant.now().minusSeconds(86400));
        openTask.setAcknowledgedByUser(owner);
        openTask.setSnoozedUntil(LocalDate.now().minusDays(1));

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(deadLetterWorkflowTaskService.performanceSummary(organizationId, 6))
                .thenReturn(new DeadLetterSupportPerformanceSummary(
                        LocalDate.now().minusWeeks(5),
                        LocalDate.now().plusDays(2),
                        6,
                        4,
                        0.5,
                        30.0,
                        60.0,
                        true,
                        true,
                        true,
                        DeadLetterSupportPerformanceStatus.AT_RISK));
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId,
                WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE,
                WorkflowTaskStatus.OPEN))
                .thenReturn(List.of(openTask));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getId() == null) {
                setId(notification, UUID.randomUUID());
            }
            return notification;
        });

        DeadLetterSupportPerformanceMonitorRunResult result = service.syncOrganization(organizationId);

        assertThat(result.createdCount()).isZero();
        assertThat(result.closedCount()).isZero();
        assertThat(result.escalatedCount()).isZero();
        assertThat(openTask.getSnoozedUntil()).isNull();
        assertThat(openTask.getAcknowledgedAt()).isNull();
        assertThat(openTask.getAcknowledgedByUser()).isNull();
        verify(auditService).record(
                eq(organizationId),
                eq("DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED"),
                eq("workflow_task"),
                eq(openTask.getId().toString()),
                eq("Reactivated support performance risk after snooze expired on " + LocalDate.now().minusDays(1)));
        verify(auditService).record(
                eq(organizationId),
                eq("DEAD_LETTER_SUPPORT_PERFORMANCE_NOTIFIED"),
                eq("workflow_task"),
                eq(openTask.getId().toString()),
                eq("Notified " + owner.getEmail() + " about support performance risk"));
    }

    @Test
    void queueSummaryAndFilterReflectAcknowledgedIgnoredAndOverdueState() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);

        WorkflowTask acknowledgedAssigned = new WorkflowTask();
        setId(acknowledgedAssigned, UUID.randomUUID());
        acknowledgedAssigned.setOrganization(organization);
        acknowledgedAssigned.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        acknowledgedAssigned.setStatus(WorkflowTaskStatus.OPEN);
        acknowledgedAssigned.setAssignedToUser(user(UUID.randomUUID(), "owner@acme.test"));
        acknowledgedAssigned.setAcknowledgedAt(Instant.now().minusSeconds(3600));
        acknowledgedAssigned.setDueDate(LocalDate.now().minusDays(1));

        WorkflowTask ignoredUnassigned = new WorkflowTask();
        setId(ignoredUnassigned, UUID.randomUUID());
        ignoredUnassigned.setOrganization(organization);
        ignoredUnassigned.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        ignoredUnassigned.setStatus(WorkflowTaskStatus.OPEN);
        ignoredUnassigned.setDueDate(LocalDate.now());

        WorkflowTask assignedUnacknowledged = new WorkflowTask();
        setId(assignedUnacknowledged, UUID.randomUUID());
        assignedUnacknowledged.setOrganization(organization);
        assignedUnacknowledged.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        assignedUnacknowledged.setStatus(WorkflowTaskStatus.OPEN);
        assignedUnacknowledged.setAssignedToUser(user(UUID.randomUUID(), "admin@acme.test"));
        assignedUnacknowledged.setDueDate(LocalDate.now().plusDays(1));

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId,
                WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE,
                WorkflowTaskStatus.OPEN))
                .thenReturn(List.of(acknowledgedAssigned, ignoredUnassigned, assignedUnacknowledged));
        when(notificationRepository.countByWorkflowTaskIdAndReferenceType(
                acknowledgedAssigned.getId(),
                "dead_letter_support_performance_escalation"))
                .thenReturn(0L);
        when(notificationRepository.countByWorkflowTaskIdAndReferenceType(
                ignoredUnassigned.getId(),
                "dead_letter_support_performance_escalation"))
                .thenReturn(2L);
        when(notificationRepository.countByWorkflowTaskIdAndReferenceType(
                assignedUnacknowledged.getId(),
                "dead_letter_support_performance_escalation"))
                .thenReturn(0L);
        when(auditService.hasOrganizationEventForEntity(
                organizationId,
                "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED",
                ignoredUnassigned.getId().toString()))
                .thenReturn(true);
        when(auditService.hasOrganizationEventForEntity(
                organizationId,
                "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED",
                assignedUnacknowledged.getId().toString()))
                .thenReturn(false);

        DeadLetterSupportPerformanceTaskQueueSummary summary = service.queueSummary(organizationId);
        List<WorkflowTask> acknowledgedTasks = service.listOpenRiskTasks(
                organizationId,
                DeadLetterSupportPerformanceTaskFilter.ACKNOWLEDGED);
        List<WorkflowTask> ignoredTasks = service.listOpenRiskTasks(
                organizationId,
                DeadLetterSupportPerformanceTaskFilter.IGNORED);
        List<WorkflowTask> unassignedTasks = service.listOpenRiskTasks(
                organizationId,
                DeadLetterSupportPerformanceTaskFilter.UNASSIGNED);
        List<WorkflowTask> reactivatedNeedingAttentionTasks = service.listOpenRiskTasks(
                organizationId,
                DeadLetterSupportPerformanceTaskFilter.REACTIVATED_NEEDS_ATTENTION);
        List<WorkflowTask> reactivatedOverdueTasks = service.listOpenRiskTasks(
                organizationId,
                DeadLetterSupportPerformanceTaskFilter.REACTIVATED_OVERDUE);

        assertThat(summary.openTaskCount()).isEqualTo(3);
        assertThat(summary.assignedTaskCount()).isEqualTo(2);
        assertThat(summary.unassignedTaskCount()).isEqualTo(1);
        assertThat(summary.acknowledgedTaskCount()).isEqualTo(1);
        assertThat(summary.unacknowledgedTaskCount()).isEqualTo(2);
        assertThat(summary.snoozedTaskCount()).isZero();
        assertThat(summary.overdueTaskCount()).isEqualTo(1);
        assertThat(summary.ignoredTaskCount()).isEqualTo(1);
        assertThat(summary.reactivatedNeedsAttentionCount()).isEqualTo(1);
        assertThat(summary.reactivatedOverdueCount()).isEqualTo(0);
        assertThat(summary.secondaryEscalationCount()).isEqualTo(2);
        assertThat(acknowledgedTasks).containsExactly(acknowledgedAssigned);
        assertThat(ignoredTasks).containsExactly(ignoredUnassigned);
        assertThat(unassignedTasks).containsExactly(ignoredUnassigned);
        assertThat(reactivatedNeedingAttentionTasks).containsExactly(ignoredUnassigned);
        assertThat(reactivatedOverdueTasks).isEmpty();
    }

    @Test
    void highPriorityQueueCombinesIgnoredAndReactivatedOverdueTasks() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);

        WorkflowTask ignoredTask = new WorkflowTask();
        setId(ignoredTask, UUID.randomUUID());
        ignoredTask.setOrganization(organization);
        ignoredTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        ignoredTask.setStatus(WorkflowTaskStatus.OPEN);
        ignoredTask.setDueDate(LocalDate.now().plusDays(1));

        WorkflowTask reactivatedOverdueTask = new WorkflowTask();
        setId(reactivatedOverdueTask, UUID.randomUUID());
        reactivatedOverdueTask.setOrganization(organization);
        reactivatedOverdueTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        reactivatedOverdueTask.setStatus(WorkflowTaskStatus.OPEN);
        reactivatedOverdueTask.setDueDate(LocalDate.now().minusDays(1));

        WorkflowTask ordinaryTask = new WorkflowTask();
        setId(ordinaryTask, UUID.randomUUID());
        ordinaryTask.setOrganization(organization);
        ordinaryTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        ordinaryTask.setStatus(WorkflowTaskStatus.OPEN);
        ordinaryTask.setDueDate(LocalDate.now().plusDays(2));

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId,
                WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE,
                WorkflowTaskStatus.OPEN))
                .thenReturn(List.of(ignoredTask, reactivatedOverdueTask, ordinaryTask));
        when(notificationRepository.countByWorkflowTaskIdAndReferenceType(
                ignoredTask.getId(),
                "dead_letter_support_performance_escalation"))
                .thenReturn(2L);
        when(notificationRepository.countByWorkflowTaskIdAndReferenceType(
                reactivatedOverdueTask.getId(),
                "dead_letter_support_performance_escalation"))
                .thenReturn(0L);
        when(notificationRepository.countByWorkflowTaskIdAndReferenceType(
                ordinaryTask.getId(),
                "dead_letter_support_performance_escalation"))
                .thenReturn(0L);
        when(auditService.hasOrganizationEventForEntity(
                organizationId,
                "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED",
                ignoredTask.getId().toString()))
                .thenReturn(false);
        when(auditService.hasOrganizationEventForEntity(
                organizationId,
                "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED",
                reactivatedOverdueTask.getId().toString()))
                .thenReturn(true);
        when(auditService.hasOrganizationEventForEntity(
                organizationId,
                "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED",
                ordinaryTask.getId().toString()))
                .thenReturn(false);

        List<WorkflowTask> highPriorityTasks = service.listHighPriorityRiskTasks(organizationId);

        assertThat(highPriorityTasks).containsExactly(reactivatedOverdueTask, ignoredTask);
    }

    private Organization organization(UUID id) {
        Organization organization = new Organization();
        setId(organization, id);
        return organization;
    }

    private AppUser user(UUID id, String email) {
        AppUser user = new AppUser();
        setId(user, id);
        user.setEmail(email);
        return user;
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

    private void setCreatedAt(Notification notification, Instant createdAt) {
        try {
            Field field = Notification.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(notification, createdAt);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
