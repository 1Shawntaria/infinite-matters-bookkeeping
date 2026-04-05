package com.infinitematters.bookkeeping.workflows;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.dashboard.DashboardActionUrgency;
import com.infinitematters.bookkeeping.domain.Category;
import com.infinitematters.bookkeeping.ledger.LedgerService;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.periods.PeriodCloseService;
import com.infinitematters.bookkeeping.security.RequestIdentityService;
import com.infinitematters.bookkeeping.transactions.BookkeepingTransaction;
import com.infinitematters.bookkeeping.transactions.CategorizationDecision;
import com.infinitematters.bookkeeping.transactions.CategorizationDecisionRepository;
import com.infinitematters.bookkeeping.transactions.DecisionStatus;
import com.infinitematters.bookkeeping.transactions.TransactionStatus;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewQueueService {
    private final WorkflowTaskRepository taskRepository;
    private final CategorizationDecisionRepository decisionRepository;
    private final OrganizationService organizationService;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final PeriodCloseService periodCloseService;
    private final UserService userService;
    private final RequestIdentityService requestIdentityService;

    public ReviewQueueService(WorkflowTaskRepository taskRepository,
                              CategorizationDecisionRepository decisionRepository,
                              OrganizationService organizationService,
                              LedgerService ledgerService,
                              AuditService auditService,
                              PeriodCloseService periodCloseService,
                              UserService userService,
                              RequestIdentityService requestIdentityService) {
        this.taskRepository = taskRepository;
        this.decisionRepository = decisionRepository;
        this.organizationService = organizationService;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.periodCloseService = periodCloseService;
        this.userService = userService;
        this.requestIdentityService = requestIdentityService;
    }

    @Transactional
    public WorkflowTask createReviewTask(Organization organization, BookkeepingTransaction transaction,
                                         CategorizationDecision decision) {
        return taskRepository.findByTransactionIdAndStatus(transaction.getId(), WorkflowTaskStatus.OPEN)
                .orElseGet(() -> {
                    WorkflowTask task = new WorkflowTask();
                    task.setOrganization(organization);
                    task.setTransaction(transaction);
                    task.setTaskType(WorkflowTaskType.REVIEW_TRANSACTION);
                    task.setTitle("Review categorization for " + safeLabel(transaction.getMerchant()));
                    task.setDescription("AI needs confirmation for "
                            + safeLabel(transaction.getMerchant())
                            + " on " + transaction.getTransactionDate()
                            + " for " + transaction.getAmount()
                            + ". Proposed category: " + decision.getProposedCategory()
                            + ".");
                    task.setDueDate(LocalDate.now().plusDays(2));
                    task.setPriority(WorkflowTaskPriority.MEDIUM);
                    task.setStatus(WorkflowTaskStatus.OPEN);
                    return taskRepository.save(task);
                });
    }

    @Transactional(readOnly = true)
    public List<ReviewTaskSummary> listOpenReviews(UUID organizationId) {
        organizationService.get(organizationId);
        return taskRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(organizationId, WorkflowTaskStatus.OPEN)
                .stream()
                .map(task -> {
                    if (task.getTransaction() == null) {
                        return new ReviewTaskSummary(
                                task.getId(),
                                null,
                                task.getNotification() != null ? task.getNotification().getId() : null,
                                task.getTaskType().name(),
                                task.getPriority().name(),
                                isOverdue(task),
                                task.getTitle(),
                                task.getDescription(),
                                task.getDueDate(),
                                task.getAssignedToUser() != null ? task.getAssignedToUser().getId() : null,
                                task.getAssignedToUser() != null ? task.getAssignedToUser().getFullName() : null,
                                null,
                                null,
                                null,
                                null,
                                0.0,
                                null,
                                task.getResolutionComment(),
                                task.getAcknowledgedByUser() != null ? task.getAcknowledgedByUser().getId() : null,
                                task.getAcknowledgedAt(),
                                task.getSnoozedUntil(),
                                task.getResolvedByUser() != null ? task.getResolvedByUser().getId() : null,
                                task.getResolvedAt());
                    }
                    CategorizationDecision decision = latestDecision(task.getTransaction().getId());
                    BookkeepingTransaction transaction = task.getTransaction();
                    return new ReviewTaskSummary(
                            task.getId(),
                            transaction.getId(),
                            task.getNotification() != null ? task.getNotification().getId() : null,
                            task.getTaskType().name(),
                            task.getPriority().name(),
                            isOverdue(task),
                            task.getTitle(),
                            task.getDescription(),
                            task.getDueDate(),
                            task.getAssignedToUser() != null ? task.getAssignedToUser().getId() : null,
                            task.getAssignedToUser() != null ? task.getAssignedToUser().getFullName() : null,
                            transaction.getMerchant(),
                            transaction.getAmount(),
                            transaction.getTransactionDate(),
                            decision.getProposedCategory(),
                            decision.getConfidenceScore(),
                            decision.getRoute(),
                            task.getResolutionComment(),
                            task.getAcknowledgedByUser() != null ? task.getAcknowledgedByUser().getId() : null,
                            task.getAcknowledgedAt(),
                            task.getSnoozedUntil(),
                            task.getResolvedByUser() != null ? task.getResolvedByUser().getId() : null,
                            task.getResolvedAt());
                })
                .toList();
    }

    @Transactional
    public ReviewResolutionResult acceptSuggestedCategory(UUID organizationId, UUID taskId) {
        WorkflowTask task = getOpenTask(organizationId, taskId);
        requireTransactionReviewTask(task);
        CategorizationDecision decision = latestDecision(task.getTransaction().getId());
        Category category = decision.getProposedCategory();
        return resolveTask(task, decision, category, null);
    }

    @Transactional
    public ReviewResolutionResult overrideCategory(UUID organizationId, UUID taskId, Category category, String resolutionComment) {
        WorkflowTask task = getOpenTask(organizationId, taskId);
        requireTransactionReviewTask(task);
        CategorizationDecision decision = latestDecision(task.getTransaction().getId());
        return resolveTask(task, decision, category, resolutionComment);
    }

    @Transactional
    public ReviewTaskSummary assignTask(UUID organizationId, UUID taskId, UUID assignedUserId) {
        WorkflowTask task = getOpenTask(organizationId, taskId);
        if (!userService.hasAccess(organizationId, assignedUserId)) {
            throw new IllegalArgumentException("Assigned user is not a member of organization " + organizationId);
        }
        AppUser assignee = userService.get(assignedUserId);
        task.setAssignedToUser(assignee);
        taskRepository.save(task);
        auditService.record(organizationId, "WORKFLOW_TASK_ASSIGNED", "workflow_task", task.getId().toString(),
                "Assigned task to " + assignee.getFullName());
        return toSummary(task);
    }

    @Transactional(readOnly = true)
    public WorkflowInboxSummary inbox(UUID organizationId, UUID currentUserId) {
        organizationService.get(organizationId);
        List<WorkflowTask> openTasks = taskRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(
                organizationId, WorkflowTaskStatus.OPEN);
        LocalDate today = LocalDate.now();
        long overdueCount = openTasks.stream().filter(this::isOverdue).count();
        long dueTodayCount = openTasks.stream()
                .filter(task -> task.getDueDate() != null && task.getDueDate().isEqual(today))
                .count();
        long highPriorityCount = openTasks.stream()
                .filter(task -> task.getPriority() == WorkflowTaskPriority.HIGH || task.getPriority() == WorkflowTaskPriority.CRITICAL)
                .count();
        long unassignedCount = openTasks.stream().filter(task -> task.getAssignedToUser() == null).count();
        long assignedToCurrentUserCount = openTasks.stream()
                .filter(task -> task.getAssignedToUser() != null && task.getAssignedToUser().getId().equals(currentUserId))
                .count();

        List<ReviewTaskSummary> attention = openTasks.stream()
                .sorted((left, right) -> {
                    int priorityCompare = Integer.compare(priorityRank(right.getPriority()), priorityRank(left.getPriority()));
                    if (priorityCompare != 0) {
                        return priorityCompare;
                    }
                    LocalDate leftDue = left.getDueDate() != null ? left.getDueDate() : LocalDate.MAX;
                    LocalDate rightDue = right.getDueDate() != null ? right.getDueDate() : LocalDate.MAX;
                    return leftDue.compareTo(rightDue);
                })
                .limit(5)
                .map(this::toSummary)
                .toList();

        return new WorkflowInboxSummary(
                "workflow-inbox",
                openTasks.size(),
                Math.toIntExact(overdueCount),
                Math.toIntExact(dueTodayCount),
                Math.toIntExact(highPriorityCount),
                Math.toIntExact(unassignedCount),
                Math.toIntExact(assignedToCurrentUserCount),
                recommendedInboxAction(overdueCount, highPriorityCount, openTasks.size()),
                recommendedInboxActionKey(overdueCount, highPriorityCount, openTasks.size()),
                recommendedInboxActionPath(overdueCount, highPriorityCount, openTasks.size()),
                recommendedInboxActionUrgency(overdueCount, highPriorityCount, openTasks.size()),
                attention);
    }

    @Transactional
    public ReviewTaskSummary resolveReconciliationException(UUID organizationId, UUID taskId, String resolutionComment) {
        WorkflowTask task = getOpenTask(organizationId, taskId);
        if (task.getTaskType() != WorkflowTaskType.RECONCILIATION_EXCEPTION) {
            throw new IllegalArgumentException("Task is not a reconciliation exception: " + taskId);
        }
        AppUser actor = requestIdentityService.currentUserId().map(userService::get)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user is required"));
        task.setStatus(WorkflowTaskStatus.COMPLETED);
        task.setResolutionComment(resolutionComment);
        task.setResolvedByUser(actor);
        task.setResolvedAt(Instant.now());
        taskRepository.save(task);
        auditService.record(organizationId, "RECONCILIATION_EXCEPTION_MANUALLY_RESOLVED", "workflow_task",
                task.getId().toString(), resolutionComment);
        return toSummary(task);
    }

    private ReviewResolutionResult resolveTask(WorkflowTask task, CategorizationDecision decision, Category category,
                                               String resolutionComment) {
        periodCloseService.assertPeriodOpen(task.getOrganization().getId(), task.getTransaction().getTransactionDate());
        decision.setFinalCategory(category);
        decision.setStatus(DecisionStatus.USER_CONFIRMED);
        decisionRepository.save(decision);

        BookkeepingTransaction transaction = task.getTransaction();
        transaction.setStatus(TransactionStatus.READY_TO_POST);
        ledgerService.ensurePosted(transaction, decision);

        task.setStatus(WorkflowTaskStatus.COMPLETED);
        task.setResolutionComment(resolutionComment);
        task.setResolvedAt(Instant.now());
        requestIdentityService.currentUserId().map(userService::get).ifPresent(task::setResolvedByUser);
        taskRepository.save(task);
        auditService.record(task.getOrganization().getId(), "REVIEW_RESOLVED", "workflow_task", task.getId().toString(),
                "Resolved transaction " + transaction.getId() + " with category " + category);

        return new ReviewResolutionResult(
                task.getId(),
                transaction.getId(),
                category,
                transaction.getStatus(),
                task.getStatus());
    }

    public ReviewTaskSummary toSummary(WorkflowTask task) {
        if (task.getTransaction() == null) {
            return new ReviewTaskSummary(
                    task.getId(),
                    null,
                    task.getNotification() != null ? task.getNotification().getId() : null,
                    task.getTaskType().name(),
                    task.getPriority().name(),
                    isOverdue(task),
                    task.getTitle(),
                    task.getDescription(),
                    task.getDueDate(),
                    task.getAssignedToUser() != null ? task.getAssignedToUser().getId() : null,
                    task.getAssignedToUser() != null ? task.getAssignedToUser().getFullName() : null,
                    null,
                    null,
                    null,
                    null,
                    0.0,
                    null,
                    task.getResolutionComment(),
                    task.getAcknowledgedByUser() != null ? task.getAcknowledgedByUser().getId() : null,
                    task.getAcknowledgedAt(),
                    task.getSnoozedUntil(),
                    task.getResolvedByUser() != null ? task.getResolvedByUser().getId() : null,
                    task.getResolvedAt());
        }
        CategorizationDecision decision = latestDecision(task.getTransaction().getId());
        BookkeepingTransaction transaction = task.getTransaction();
        return new ReviewTaskSummary(
                task.getId(),
                transaction.getId(),
                task.getNotification() != null ? task.getNotification().getId() : null,
                task.getTaskType().name(),
                task.getPriority().name(),
                isOverdue(task),
                task.getTitle(),
                task.getDescription(),
                task.getDueDate(),
                task.getAssignedToUser() != null ? task.getAssignedToUser().getId() : null,
                task.getAssignedToUser() != null ? task.getAssignedToUser().getFullName() : null,
                transaction.getMerchant(),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                decision.getProposedCategory(),
                decision.getConfidenceScore(),
                decision.getRoute(),
                task.getResolutionComment(),
                task.getAcknowledgedByUser() != null ? task.getAcknowledgedByUser().getId() : null,
                task.getAcknowledgedAt(),
                task.getSnoozedUntil(),
                task.getResolvedByUser() != null ? task.getResolvedByUser().getId() : null,
                task.getResolvedAt());
    }

    private WorkflowTask getOpenTask(UUID organizationId, UUID taskId) {
        return taskRepository.findById(taskId)
                .filter(task -> task.getStatus() == WorkflowTaskStatus.OPEN)
                .filter(task -> task.getOrganization().getId().equals(organizationId))
                .orElseThrow(() -> new IllegalArgumentException("Open review task not found: " + taskId));
    }

    private void requireTransactionReviewTask(WorkflowTask task) {
        if (task.getTaskType() != WorkflowTaskType.REVIEW_TRANSACTION || task.getTransaction() == null) {
            throw new IllegalArgumentException("Task is not a transaction review task: " + task.getId());
        }
    }

    private CategorizationDecision latestDecision(UUID transactionId) {
        return decisionRepository.findTopByTransactionIdOrderByCreatedAtDesc(transactionId)
                .orElseThrow(() -> new IllegalStateException("No categorization decision for transaction " + transactionId));
    }

    private String safeLabel(String merchant) {
        return merchant == null || merchant.isBlank() ? "transaction" : merchant;
    }

    private boolean isOverdue(WorkflowTask task) {
        return task.getDueDate() != null && task.getDueDate().isBefore(LocalDate.now());
    }

    private int priorityRank(WorkflowTaskPriority priority) {
        return switch (priority) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case CRITICAL -> 4;
        };
    }

    private String recommendedInboxAction(long overdueCount, long highPriorityCount, int openCount) {
        if (overdueCount > 0) {
            return "Resolve overdue bookkeeping tasks";
        }
        if (highPriorityCount > 0) {
            return "Review high-priority bookkeeping tasks";
        }
        if (openCount > 0) {
            return "Review pending bookkeeping tasks";
        }
        return null;
    }

    private String recommendedInboxActionKey(long overdueCount, long highPriorityCount, int openCount) {
        if (overdueCount > 0) {
            return "RESOLVE_OVERDUE_TASKS";
        }
        if (highPriorityCount > 0) {
            return "REVIEW_HIGH_PRIORITY_TASKS";
        }
        if (openCount > 0) {
            return "REVIEW_PENDING_TASKS";
        }
        return null;
    }

    private String recommendedInboxActionPath(long overdueCount, long highPriorityCount, int openCount) {
        if (overdueCount > 0 || highPriorityCount > 0 || openCount > 0) {
            return "/workflows/inbox";
        }
        return null;
    }

    private DashboardActionUrgency recommendedInboxActionUrgency(long overdueCount, long highPriorityCount, int openCount) {
        if (overdueCount > 0 || highPriorityCount > 0) {
            return DashboardActionUrgency.HIGH;
        }
        if (openCount > 0) {
            return DashboardActionUrgency.NORMAL;
        }
        return null;
    }
}
