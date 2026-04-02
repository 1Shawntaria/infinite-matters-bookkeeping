package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterSupportPerformanceMonitorServiceTests {

    @Mock
    private DeadLetterWorkflowTaskService deadLetterWorkflowTaskService;

    @Mock
    private WorkflowTaskRepository workflowTaskRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private AuditService auditService;

    private DeadLetterSupportPerformanceMonitorService service;

    @BeforeEach
    void setUp() {
        service = new DeadLetterSupportPerformanceMonitorService(
                deadLetterWorkflowTaskService,
                workflowTaskRepository,
                organizationService,
                auditService);
    }

    @Test
    void createsPerformanceRiskTaskWhenSupportIsAtRisk() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);
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
        when(workflowTaskRepository.save(any(WorkflowTask.class))).thenAnswer(invocation -> {
            WorkflowTask task = invocation.getArgument(0);
            if (task.getId() == null) {
                setId(task, UUID.randomUUID());
            }
            return task;
        });

        DeadLetterSupportPerformanceMonitorRunResult result = service.syncOrganization(organizationId);

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.closedCount()).isZero();

        ArgumentCaptor<WorkflowTask> taskCaptor = ArgumentCaptor.forClass(WorkflowTask.class);
        verify(workflowTaskRepository).save(taskCaptor.capture());
        WorkflowTask saved = taskCaptor.getValue();
        assertThat(saved.getTaskType()).isEqualTo(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        assertThat(saved.getPriority()).isEqualTo(WorkflowTaskPriority.CRITICAL);
        assertThat(saved.getStatus()).isEqualTo(WorkflowTaskStatus.OPEN);
        assertThat(saved.getTitle()).contains("performance at risk");
        assertThat(saved.getDescription()).contains("Ignored escalation rate=50%");
        verify(auditService).record(
                eq(organizationId),
                eq("DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_CREATED"),
                eq("workflow_task"),
                any(),
                any());
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

    private Organization organization(UUID id) {
        Organization organization = new Organization();
        setId(organization, id);
        return organization;
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
}
