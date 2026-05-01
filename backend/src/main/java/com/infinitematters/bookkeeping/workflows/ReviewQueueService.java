package com.infinitematters.bookkeeping.workflows;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.dashboard.DashboardActionUrgency;
import com.infinitematters.bookkeeping.domain.Category;
import com.infinitematters.bookkeeping.ledger.LedgerService;
import com.infinitematters.bookkeeping.notifications.CloseControlDisposition;
import com.infinitematters.bookkeeping.notifications.Notification;
import com.infinitematters.bookkeeping.notifications.NotificationRepository;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.periods.AccountingPeriod;
import com.infinitematters.bookkeeping.periods.AccountingPeriodRepository;
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
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

@Service
public class ReviewQueueService {
    private final WorkflowTaskRepository taskRepository;
    private final CategorizationDecisionRepository decisionRepository;
    private final OrganizationService organizationService;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final PeriodCloseService periodCloseService;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final UserService userService;
    private final RequestIdentityService requestIdentityService;
    private final NotificationRepository notificationRepository;

    public ReviewQueueService(WorkflowTaskRepository taskRepository,
                              CategorizationDecisionRepository decisionRepository,
                              OrganizationService organizationService,
                              LedgerService ledgerService,
                              AuditService auditService,
                              PeriodCloseService periodCloseService,
                              AccountingPeriodRepository accountingPeriodRepository,
                              UserService userService,
                              RequestIdentityService requestIdentityService,
                              NotificationRepository notificationRepository) {
        this.taskRepository = taskRepository;
        this.decisionRepository = decisionRepository;
        this.organizationService = organizationService;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.periodCloseService = periodCloseService;
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.userService = userService;
        this.requestIdentityService = requestIdentityService;
        this.notificationRepository = notificationRepository;
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
                            null,
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

        List<ReviewTaskSummary> syntheticAttention = closeControlAttentionTasks(organizationId, currentUserId, today);
        long syntheticHighPriorityCount = syntheticAttention.stream()
                .filter(task -> "HIGH".equals(task.priority()) || "CRITICAL".equals(task.priority()))
                .count();
        long syntheticUnassignedCount = syntheticAttention.stream()
                .filter(task -> task.assignedToUserId() == null)
                .count();
        long syntheticAssignedToCurrentUserCount = syntheticAttention.stream()
                .filter(task -> task.assignedToUserId() != null && task.assignedToUserId().equals(currentUserId))
                .count();
        long syntheticDueTodayCount = syntheticAttention.stream()
                .filter(task -> task.dueDate() != null && task.dueDate().isEqual(today))
                .count();
        long syntheticOverdueCount = syntheticAttention.stream()
                .filter(ReviewTaskSummary::overdue)
                .count();

        List<ReviewTaskSummary> attention = new ArrayList<>(openTasks.stream()
                .sorted((left, right) -> {
                    int priorityCompare = Integer.compare(priorityRank(right.getPriority()), priorityRank(left.getPriority()));
                    if (priorityCompare != 0) {
                        return priorityCompare;
                    }
                    LocalDate leftDue = left.getDueDate() != null ? left.getDueDate() : LocalDate.MAX;
                    LocalDate rightDue = right.getDueDate() != null ? right.getDueDate() : LocalDate.MAX;
                    return leftDue.compareTo(rightDue);
                })
                .map(this::toSummary)
                .toList());
        attention.addAll(syntheticAttention);
        attention = attention.stream()
                .sorted((left, right) -> {
                    int priorityCompare = Integer.compare(priorityRank(right.priority()), priorityRank(left.priority()));
                    if (priorityCompare != 0) {
                        return priorityCompare;
                    }
                    LocalDate leftDue = left.dueDate() != null ? left.dueDate() : LocalDate.MAX;
                    LocalDate rightDue = right.dueDate() != null ? right.dueDate() : LocalDate.MAX;
                    return leftDue.compareTo(rightDue);
                })
                .limit(5)
                .toList();

        InboxRecommendation recommendation = recommendedInboxAction(
                organizationId,
                attention,
                overdueCount + syntheticOverdueCount,
                highPriorityCount + syntheticHighPriorityCount,
                openTasks.size() + syntheticAttention.size());

        return new WorkflowInboxSummary(
                "workflow-inbox",
                openTasks.size() + syntheticAttention.size(),
                Math.toIntExact(overdueCount + syntheticOverdueCount),
                Math.toIntExact(dueTodayCount + syntheticDueTodayCount),
                Math.toIntExact(highPriorityCount + syntheticHighPriorityCount),
                Math.toIntExact(unassignedCount + syntheticUnassignedCount),
                Math.toIntExact(assignedToCurrentUserCount + syntheticAssignedToCurrentUserCount),
                recommendation != null ? recommendation.label() : null,
                recommendation != null ? recommendation.key() : null,
                recommendation != null ? recommendation.path() : null,
                recommendation != null ? recommendation.urgency() : null,
                attention);
    }

    @Transactional(readOnly = true)
    public List<ReviewTaskSummary> listCloseControlAttentionTasks(UUID organizationId) {
        organizationService.get(organizationId);
        return closeControlAttentionTasks(organizationId, null, LocalDate.now());
    }

    @Transactional
    public ReviewTaskSummary acknowledgeCloseControlTask(UUID organizationId, UUID taskId, UUID actorUserId, String note) {
        ReviewTaskSummary task = requireCurrentCloseControlTask(organizationId, taskId, actorUserId);
        auditService.recordForUser(
                actorUserId,
                organizationId,
                "CLOSE_CONTROL_TASK_ACKNOWLEDGED",
                "workflow_task",
                task.taskId().toString(),
                note != null && !note.isBlank() ? note.trim() : "Close control task acknowledged");
        return requireCurrentCloseControlTask(organizationId, taskId, actorUserId);
    }

    @Transactional
    public void resolveCloseControlTask(UUID organizationId, UUID taskId, UUID actorUserId, String note) {
        ReviewTaskSummary task = requireCurrentCloseControlTask(organizationId, taskId, actorUserId);
        if (!"FORCE_CLOSE_REVIEW".equals(task.taskType())) {
            throw new IllegalArgumentException("Only force-close review tasks can be resolved manually");
        }
        auditService.recordForUser(
                actorUserId,
                organizationId,
                "CLOSE_CONTROL_TASK_RESOLVED",
                "workflow_task",
                task.taskId().toString(),
                note != null && !note.isBlank() ? note.trim() : "Force-close review completed");
    }

    private List<ReviewTaskSummary> closeControlAttentionTasks(UUID organizationId, UUID currentUserId, LocalDate today) {
        List<ReviewTaskSummary> attention = new ArrayList<>();

        auditService.listRecentForOrganizationByEventType(organizationId, "PERIOD_CLOSE_ATTESTATION_UPDATED", 5)
                .stream()
                .filter(event -> event.entityId() != null)
                .filter(event -> auditService.listForOrganizationByEventTypeAndEntity(
                                organizationId,
                                "PERIOD_CLOSE_ATTESTED",
                                event.entityId()).stream()
                        .noneMatch(attested -> !attested.createdAt().isBefore(event.createdAt())))
                .findFirst()
                .flatMap(event -> toCloseControlTask(
                        organizationId,
                        event,
                        "CLOSE_ATTESTATION_FOLLOW_UP",
                        "HIGH",
                        "Confirm month-end attestation for " + event.entityId(),
                        "Attestation routing or summary was updated, but the month still does not show a recorded confirmation from the assigned approver.",
                        today))
                .ifPresent(attention::add);

        auditService.listRecentForOrganizationByEventType(organizationId, "PERIOD_FORCE_CLOSED", 5)
                .stream()
                .filter(event -> event.entityId() != null)
                .findFirst()
                .flatMap(event -> toCloseControlTask(
                        organizationId,
                        event,
                        "FORCE_CLOSE_REVIEW",
                        "HIGH",
                        "Review force-close controls for " + event.entityId(),
                        "A recent month was force-closed. Revisit the close story and verify the override is fully documented and understood.",
                        today))
                .ifPresent(attention::add);

        return attention;
    }

    private java.util.Optional<ReviewTaskSummary> toCloseControlTask(UUID organizationId,
                                                                     com.infinitematters.bookkeeping.audit.AuditEventSummary event,
                                                                     String taskType,
                                                                     String priority,
                                                                     String title,
                                                                     String description,
                                                                     LocalDate today) {
        YearMonth month;
        try {
            month = YearMonth.parse(event.entityId());
        } catch (RuntimeException ex) {
            return java.util.Optional.empty();
        }

        AccountingPeriod period = accountingPeriodRepository.findPeriodContaining(organizationId, month.atDay(1))
                .orElse(null);
        UUID assignedUserId = null;
        String assignedUserName = null;
        if ("CLOSE_ATTESTATION_FOLLOW_UP".equals(taskType) && period != null && period.getCloseApproverUser() != null) {
            assignedUserId = period.getCloseApproverUser().getId();
            assignedUserName = period.getCloseApproverUser().getFullName();
        } else if (period != null && period.getCloseOwnerUser() != null) {
            assignedUserId = period.getCloseOwnerUser().getId();
            assignedUserName = period.getCloseOwnerUser().getFullName();
        }

        LocalDate dueDate = event.createdAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate().plusDays(1);
        boolean overdue = dueDate.isBefore(today);
        UUID taskId = UUID.nameUUIDFromBytes((taskType + ":" + organizationId + ":" + event.entityId()).getBytes(StandardCharsets.UTF_8));
        Optional<com.infinitematters.bookkeeping.audit.AuditEventSummary> latestAcknowledgement =
                latestCloseControlAction(organizationId, "CLOSE_CONTROL_TASK_ACKNOWLEDGED", taskId, event.createdAt());
        Optional<com.infinitematters.bookkeeping.audit.AuditEventSummary> latestResolution =
                latestCloseControlAction(organizationId, "CLOSE_CONTROL_TASK_RESOLVED", taskId, event.createdAt());
        if ("FORCE_CLOSE_REVIEW".equals(taskType) && latestResolution.isPresent()) {
            return java.util.Optional.empty();
        }

        CloseControlDisposition disposition = latestCloseControlDisposition(organizationId, taskId)
                .orElse(defaultDisposition(taskType));
        LocalDate effectiveDueDate = effectiveCloseControlDueDate(disposition, dueDate);
        boolean effectiveOverdue = effectiveDueDate.isBefore(today);
        String effectivePriority = effectiveCloseControlPriority(taskType, disposition);
        String effectiveTitle = effectiveCloseControlTitle(title, month, disposition);
        String effectiveDescription = effectiveCloseControlDescription(description, month, disposition, assignedUserName);

        return java.util.Optional.of(new ReviewTaskSummary(
                taskId,
                null,
                null,
                taskType,
                effectivePriority,
                effectiveOverdue,
                effectiveTitle,
                effectiveDescription,
                effectiveDueDate,
                assignedUserId,
                assignedUserName,
                null,
                null,
                null,
                null,
                0.0,
                null,
                "/close?month=" + month,
                event.details(),
                latestAcknowledgement.map(com.infinitematters.bookkeeping.audit.AuditEventSummary::actorUserId).orElse(null),
                latestAcknowledgement.map(com.infinitematters.bookkeeping.audit.AuditEventSummary::createdAt).orElse(null),
                null,
                latestResolution.map(com.infinitematters.bookkeeping.audit.AuditEventSummary::actorUserId).orElse(null),
                latestResolution.map(com.infinitematters.bookkeeping.audit.AuditEventSummary::createdAt).orElse(null)));
    }

    private Optional<CloseControlDisposition> latestCloseControlDisposition(UUID organizationId, UUID taskId) {
        return notificationRepository
                .findTopByOrganizationIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                        organizationId,
                        "close_control_follow_up_escalation",
                        taskId.toString())
                .map(Notification::getCloseControlDisposition);
    }

    private CloseControlDisposition defaultDisposition(String taskType) {
        if ("FORCE_CLOSE_REVIEW".equals(taskType)) {
            return CloseControlDisposition.OVERRIDE_DOCS_IN_PROGRESS;
        }
        return CloseControlDisposition.WAITING_ON_APPROVER;
    }

    private LocalDate effectiveCloseControlDueDate(CloseControlDisposition disposition, LocalDate baseDueDate) {
        if (disposition == CloseControlDisposition.REVISIT_TOMORROW) {
            return LocalDate.now().plusDays(1);
        }
        return baseDueDate;
    }

    private String effectiveCloseControlPriority(String taskType, CloseControlDisposition disposition) {
        if (disposition == CloseControlDisposition.REVISIT_TOMORROW) {
            return "MEDIUM";
        }
        if ("FORCE_CLOSE_REVIEW".equals(taskType) && disposition == CloseControlDisposition.OVERRIDE_DOCS_IN_PROGRESS) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private String effectiveCloseControlTitle(String title, YearMonth month, CloseControlDisposition disposition) {
        return switch (disposition) {
            case WAITING_ON_APPROVER -> title;
            case OVERRIDE_DOCS_IN_PROGRESS -> "Finish override documentation for " + month;
            case REVISIT_TOMORROW -> "Revisit close follow-up tomorrow for " + month;
        };
    }

    private String effectiveCloseControlDescription(String description,
                                                    YearMonth month,
                                                    CloseControlDisposition disposition,
                                                    String assignedUserName) {
        return switch (disposition) {
            case WAITING_ON_APPROVER -> assignedUserName != null
                    ? description + " Keep " + assignedUserName + " moving so " + month + " can clear final attestation."
                    : description;
            case OVERRIDE_DOCS_IN_PROGRESS -> "Owner review already confirmed that override support is being documented for " + month
                    + ". Finish the close memo, evidence, and rationale before treating the month as settled.";
            case REVISIT_TOMORROW -> "Owner review intentionally deferred this close-control follow-up until tomorrow. Keep the plan visible, but avoid sending the team back into the month early unless risk changes.";
        };
    }

    private Optional<com.infinitematters.bookkeeping.audit.AuditEventSummary> latestCloseControlAction(UUID organizationId,
                                                                                                         String eventType,
                                                                                                         UUID taskId,
                                                                                                         Instant sourceCreatedAt) {
        return auditService.listForOrganizationByEventTypeAndEntity(organizationId, eventType, taskId.toString())
                .stream()
                .filter(event -> !event.createdAt().isBefore(sourceCreatedAt))
                .max(Comparator.comparing(com.infinitematters.bookkeeping.audit.AuditEventSummary::createdAt));
    }

    private ReviewTaskSummary requireCurrentCloseControlTask(UUID organizationId, UUID taskId, UUID currentUserId) {
        return closeControlAttentionTasks(organizationId, currentUserId, LocalDate.now()).stream()
                .filter(task -> task.taskId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Close control task not found: " + taskId));
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
                null,
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

    private int priorityRank(String priority) {
        return switch (priority) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            case "CRITICAL" -> 4;
            default -> 0;
        };
    }

    private InboxRecommendation recommendedInboxAction(UUID organizationId,
                                                       List<ReviewTaskSummary> attentionTasks,
                                                       long overdueCount,
                                                       long highPriorityCount,
                                                       int openCount) {
        Optional<InboxRecommendation> closeControlRecommendation = recommendedCloseControlAction(organizationId, attentionTasks);
        if (closeControlRecommendation.isPresent()) {
            return closeControlRecommendation.get();
        }
        if (overdueCount > 0) {
            return new InboxRecommendation(
                    "Resolve overdue bookkeeping tasks",
                    "RESOLVE_OVERDUE_TASKS",
                    "/workflows/inbox",
                    DashboardActionUrgency.HIGH);
        }
        if (highPriorityCount > 0) {
            return new InboxRecommendation(
                    "Review high-priority bookkeeping tasks",
                    "REVIEW_HIGH_PRIORITY_TASKS",
                    "/workflows/inbox",
                    DashboardActionUrgency.HIGH);
        }
        if (openCount > 0) {
            return new InboxRecommendation(
                    "Review pending bookkeeping tasks",
                    "REVIEW_PENDING_TASKS",
                    "/workflows/inbox",
                    DashboardActionUrgency.NORMAL);
        }
        return null;
    }

    private Optional<InboxRecommendation> recommendedCloseControlAction(UUID organizationId,
                                                                       List<ReviewTaskSummary> attentionTasks) {
        return attentionTasks.stream()
                .filter(task -> "CLOSE_ATTESTATION_FOLLOW_UP".equals(task.taskType())
                        || "FORCE_CLOSE_REVIEW".equals(task.taskType()))
                .findFirst()
                .map(task -> {
                    CloseControlDisposition disposition = latestCloseControlDisposition(organizationId, task.taskId())
                            .orElse(defaultDisposition(task.taskType()));
                    return switch (disposition) {
                        case WAITING_ON_APPROVER -> new InboxRecommendation(
                                "Push approver follow-through",
                                "PUSH_APPROVER_FOLLOW_THROUGH",
                                task.actionPath() != null ? task.actionPath() : "/close",
                                DashboardActionUrgency.HIGH);
                        case OVERRIDE_DOCS_IN_PROGRESS -> new InboxRecommendation(
                                "Finish override documentation",
                                "FINISH_OVERRIDE_DOCUMENTATION",
                                task.actionPath() != null ? task.actionPath() : "/close",
                                DashboardActionUrgency.NORMAL);
                        case REVISIT_TOMORROW -> new InboxRecommendation(
                                "Queue tomorrow's close follow-up",
                                "QUEUE_TOMORROWS_CLOSE_FOLLOW_UP",
                                task.actionPath() != null ? task.actionPath() : "/close",
                                DashboardActionUrgency.NORMAL);
                    };
                });
    }
}
