package com.infinitematters.bookkeeping.reconciliation;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.security.RequestIdentityService;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskRepository;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskPriority;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskStatus;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.time.YearMonth;
import java.util.UUID;

@Service
public class ReconciliationExceptionService {
    private final WorkflowTaskRepository workflowTaskRepository;
    private final AuditService auditService;
    private final RequestIdentityService requestIdentityService;
    private final UserService userService;

    public ReconciliationExceptionService(WorkflowTaskRepository workflowTaskRepository,
                                          AuditService auditService,
                                          RequestIdentityService requestIdentityService,
                                          UserService userService) {
        this.workflowTaskRepository = workflowTaskRepository;
        this.auditService = auditService;
        this.requestIdentityService = requestIdentityService;
        this.userService = userService;
    }

    @Transactional
    public WorkflowTask ensureVarianceTask(ReconciliationSession session) {
        String description = "Reconciliation variance for " + session.getFinancialAccount().getName()
                + " in " + session.getPeriodStart() + " to " + session.getPeriodEnd()
                + ": " + session.getVarianceAmount();

        return workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusAndDescription(
                        session.getOrganization().getId(),
                        WorkflowTaskType.RECONCILIATION_EXCEPTION,
                        WorkflowTaskStatus.OPEN,
                        description)
                .orElseGet(() -> {
                    WorkflowTask task = new WorkflowTask();
                    task.setOrganization(session.getOrganization());
                    task.setTaskType(WorkflowTaskType.RECONCILIATION_EXCEPTION);
                    task.setTitle("Resolve reconciliation variance for " + session.getFinancialAccount().getName());
                    task.setDescription(description);
                    task.setDueDate(LocalDate.now().plusDays(1));
                    task.setRelatedPeriodStart(session.getPeriodStart());
                    task.setRelatedPeriodEnd(session.getPeriodEnd());
                    task.setPriority(WorkflowTaskPriority.HIGH);
                    task.setStatus(WorkflowTaskStatus.OPEN);
                    task = workflowTaskRepository.save(task);
                    auditService.record(session.getOrganization().getId(), "RECONCILIATION_EXCEPTION_CREATED",
                            "workflow_task", task.getId().toString(), description);
                    return task;
                });
    }

    @Transactional
    public void resolveVarianceTasks(ReconciliationSession session) {
        List<WorkflowTask> tasks = workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                session.getOrganization().getId(),
                WorkflowTaskType.RECONCILIATION_EXCEPTION,
                WorkflowTaskStatus.OPEN);

        String periodMarker = session.getPeriodStart() + " to " + session.getPeriodEnd();
        String accountName = session.getFinancialAccount().getName();

        tasks.stream()
                .filter(task -> task.getDescription().contains(accountName) && task.getDescription().contains(periodMarker))
                .forEach(task -> {
                    task.setStatus(WorkflowTaskStatus.COMPLETED);
                    task.setResolutionComment("Balanced by subsequent reconciliation");
                    task.setResolvedAt(Instant.now());
                    requestIdentityService.currentUserId().map(userService::get).ifPresent(task::setResolvedByUser);
                    workflowTaskRepository.save(task);
                    auditService.record(session.getOrganization().getId(), "RECONCILIATION_EXCEPTION_RESOLVED",
                            "workflow_task", task.getId().toString(),
                            "Resolved reconciliation variance task after successful reconciliation");
                });
    }

    @Transactional
    public void forceResolveTasksForPeriod(UUID organizationId, YearMonth month, String reason) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        List<WorkflowTask> tasks = workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId,
                WorkflowTaskType.RECONCILIATION_EXCEPTION,
                WorkflowTaskStatus.OPEN);
        tasks.stream()
                .filter(task -> start.equals(task.getRelatedPeriodStart()) && end.equals(task.getRelatedPeriodEnd()))
                .forEach(task -> {
                    task.setStatus(WorkflowTaskStatus.COMPLETED);
                    task.setResolutionComment("Force-closed period with approval: " + reason);
                    task.setResolvedAt(Instant.now());
                    requestIdentityService.currentUserId().map(userService::get).ifPresent(task::setResolvedByUser);
                    workflowTaskRepository.save(task);
                    auditService.record(organizationId, "RECONCILIATION_EXCEPTION_FORCE_RESOLVED",
                            "workflow_task", task.getId().toString(),
                            "Resolved by period force close: " + reason);
                });
    }
}
