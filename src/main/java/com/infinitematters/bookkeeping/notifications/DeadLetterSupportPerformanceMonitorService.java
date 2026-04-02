package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskPriority;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskRepository;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskStatus;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class DeadLetterSupportPerformanceMonitorService {
    private final DeadLetterWorkflowTaskService deadLetterWorkflowTaskService;
    private final WorkflowTaskRepository workflowTaskRepository;
    private final OrganizationService organizationService;
    private final AuditService auditService;

    public DeadLetterSupportPerformanceMonitorService(DeadLetterWorkflowTaskService deadLetterWorkflowTaskService,
                                                      WorkflowTaskRepository workflowTaskRepository,
                                                      OrganizationService organizationService,
                                                      AuditService auditService) {
        this.deadLetterWorkflowTaskService = deadLetterWorkflowTaskService;
        this.workflowTaskRepository = workflowTaskRepository;
        this.organizationService = organizationService;
        this.auditService = auditService;
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
                workflowTaskRepository.save(created);
                auditService.record(
                        organizationId,
                        "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_CREATED",
                        "workflow_task",
                        created.getId().toString(),
                        "Created support performance risk task");
                return new DeadLetterSupportPerformanceMonitorRunResult(1, 0);
            }
            openTask.setPriority(WorkflowTaskPriority.CRITICAL);
            openTask.setDueDate(LocalDate.now());
            openTask.setTitle("Dead-letter support performance at risk");
            openTask.setDescription(buildDescription(performance));
            workflowTaskRepository.save(openTask);
            return new DeadLetterSupportPerformanceMonitorRunResult(0, 0);
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
            return new DeadLetterSupportPerformanceMonitorRunResult(0, 1);
        }
        return new DeadLetterSupportPerformanceMonitorRunResult(0, 0);
    }

    @Transactional
    public List<DeadLetterSupportPerformanceMonitorRunResult> syncAllOrganizations() {
        return organizationService.list().stream()
                .map(organization -> syncOrganization(organization.getId()))
                .toList();
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
