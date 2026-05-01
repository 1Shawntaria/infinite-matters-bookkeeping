package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.accounts.AccountType;
import com.infinitematters.bookkeeping.accounts.FinancialAccount;
import com.infinitematters.bookkeeping.accounts.FinancialAccountRepository;
import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.close.CloseChecklistSummary;
import com.infinitematters.bookkeeping.close.CloseChecklistService;
import com.infinitematters.bookkeeping.domain.Category;
import com.infinitematters.bookkeeping.notifications.NotificationService;
import com.infinitematters.bookkeeping.notifications.NotificationOperationsSummary;
import com.infinitematters.bookkeeping.notifications.NotificationSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterQueueSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterRecommendedAction;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportEffectivenessSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceMonitorService;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceTaskStatus;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportTaskOperationsSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterWorkflowTaskService;
import com.infinitematters.bookkeeping.ledger.JournalEntryRepository;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.periods.AccountingPeriod;
import com.infinitematters.bookkeeping.periods.AccountingPeriodRepository;
import com.infinitematters.bookkeeping.reconciliation.ReconciliationService;
import com.infinitematters.bookkeeping.reconciliation.ReconciliationStatus;
import com.infinitematters.bookkeeping.reconciliation.ReconciliationSummary;
import com.infinitematters.bookkeeping.transactions.BookkeepingTransaction;
import com.infinitematters.bookkeeping.transactions.BookkeepingTransactionRepository;
import com.infinitematters.bookkeeping.transactions.CategorizationDecision;
import com.infinitematters.bookkeeping.transactions.CategorizationDecisionRepository;
import com.infinitematters.bookkeeping.transactions.TransactionStatus;
import com.infinitematters.bookkeeping.workflows.ReviewQueueService;
import com.infinitematters.bookkeeping.workflows.ReviewTaskSummary;
import com.infinitematters.bookkeeping.workflows.WorkflowInboxSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    private static final long STALE_ACCOUNT_DAYS = 30;
    private static final long STALE_ACCOUNT_HIGH_URGENCY_DAYS = 60;
    private static final BigDecimal EXPENSE_CATEGORY_HIGH_URGENCY_DELTA = new BigDecimal("50.00");
    private static final DateTimeFormatter DASHBOARD_DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, uuuu");
    private static final String PERFORMANCE_REACTIVATED_EVENT_TYPE = "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED";
    private static final String HOME_CONTRACT_NEGOTIATION_POLICY =
            "If the client omits a version, the server returns the default version. "
                    + "If the client requests an unsupported version, the server returns 400 Bad Request.";
    private static final String HOME_CONTRACT_HEADER_POLICY =
            "X-Dashboard-Home-Default-Version is the server default, "
                    + "X-Dashboard-Home-Recommended-Version is the preferred client target, "
                    + "X-Dashboard-Home-Latest-Version is the newest available contract, "
                    + "and X-Dashboard-Home-Supported-Versions lists all supported versions.";

    private final OrganizationService organizationService;
    private final FinancialAccountRepository financialAccountRepository;
    private final BookkeepingTransactionRepository transactionRepository;
    private final CategorizationDecisionRepository categorizationDecisionRepository;
    private final ReviewQueueService reviewQueueService;
    private final NotificationService notificationService;
    private final DeadLetterWorkflowTaskService deadLetterWorkflowTaskService;
    private final DeadLetterSupportPerformanceMonitorService deadLetterSupportPerformanceMonitorService;
    private final CloseChecklistService closeChecklistService;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final AuditService auditService;
    private final ReconciliationService reconciliationService;

    public DashboardService(OrganizationService organizationService,
                            FinancialAccountRepository financialAccountRepository,
                            BookkeepingTransactionRepository transactionRepository,
                            CategorizationDecisionRepository categorizationDecisionRepository,
                            ReviewQueueService reviewQueueService,
                            NotificationService notificationService,
                            DeadLetterWorkflowTaskService deadLetterWorkflowTaskService,
                            DeadLetterSupportPerformanceMonitorService deadLetterSupportPerformanceMonitorService,
                            CloseChecklistService closeChecklistService,
                            JournalEntryRepository journalEntryRepository,
                            AccountingPeriodRepository accountingPeriodRepository,
                            AuditService auditService,
                            ReconciliationService reconciliationService) {
        this.organizationService = organizationService;
        this.financialAccountRepository = financialAccountRepository;
        this.transactionRepository = transactionRepository;
        this.categorizationDecisionRepository = categorizationDecisionRepository;
        this.reviewQueueService = reviewQueueService;
        this.notificationService = notificationService;
        this.deadLetterWorkflowTaskService = deadLetterWorkflowTaskService;
        this.deadLetterSupportPerformanceMonitorService = deadLetterSupportPerformanceMonitorService;
        this.closeChecklistService = closeChecklistService;
        this.journalEntryRepository = journalEntryRepository;
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.auditService = auditService;
        this.reconciliationService = reconciliationService;
    }

    @Transactional(readOnly = true)
    public DashboardSnapshot snapshot(UUID organizationId, UUID currentUserId) {
        organizationService.get(organizationId);
        YearMonth focusMonth = transactionRepository.findFirstByOrganizationIdOrderByTransactionDateDescCreatedAtDesc(organizationId)
                .map(BookkeepingTransaction::getTransactionDate)
                .map(YearMonth::from)
                .orElse(YearMonth.now());

        BigDecimal cashBalance = transactionRepository.sumAmountsByOrganizationAndStatusAndAccountTypes(
                organizationId,
                TransactionStatus.POSTED,
                List.of(AccountType.BANK, AccountType.CASH));
        List<BookkeepingTransaction> postedTransactions = transactionRepository
                .findByOrganizationIdAndStatusOrderByTransactionDateDescCreatedAtDesc(organizationId, TransactionStatus.POSTED);
        long postedTransactionCount = postedTransactions.size();

        WorkflowInboxSummary inbox = reviewQueueService.inbox(organizationId, currentUserId);
        CloseChecklistSummary checklist = closeChecklistService.checklist(organizationId, focusMonth);
        int unreconciledCount = (int) checklist.items().stream()
                .filter(item -> "ACCOUNT_RECONCILIATION".equals(item.itemType()))
                .filter(item -> !item.complete())
                .count();

        AccountingPeriod latestPeriod = accountingPeriodRepository.findFirstByOrganizationIdOrderByPeriodEndDesc(organizationId)
                .orElse(null);
        DashboardPeriodSnapshot period = new DashboardPeriodSnapshot(
                "period-close",
                checklist.closeReady(),
                unreconciledCount,
                recommendedPeriodAction(checklist.closeReady(), unreconciledCount),
                recommendedPeriodActionKey(checklist.closeReady(), unreconciledCount),
                recommendedPeriodActionPath(checklist.closeReady(), unreconciledCount),
                recommendedPeriodActionUrgency(checklist.closeReady(), unreconciledCount),
                latestPeriod != null ? latestPeriod.getStatus() : null,
                latestPeriod != null ? latestPeriod.getCloseMethod() : null,
                latestPeriod != null ? latestPeriod.getOverrideReason() : null);

        List<NotificationSummary> recentNotifications = notificationService.listForOrganization(organizationId)
                .stream()
                .limit(5)
                .toList();
        NotificationOperationsSummary notificationOperations = notificationService.operationsSummary(organizationId);
        DeadLetterQueueSummary deadLetterQueue = notificationService.deadLetterQueue(organizationId);
        DeadLetterSupportTaskOperationsSummary supportTaskOperations = deadLetterWorkflowTaskService.operationsSummary(organizationId);
        DeadLetterSupportEffectivenessSummary supportEffectiveness = deadLetterWorkflowTaskService.effectivenessSummary(organizationId, 6);
        DeadLetterSupportPerformanceSummary supportPerformance = deadLetterWorkflowTaskService.performanceSummary(organizationId, 6);
        DeadLetterSupportPerformanceTaskStatus supportPerformanceTaskStatus = deadLetterSupportPerformanceMonitorService.taskStatus(organizationId);
        List<ReviewTaskSummary> urgentRiskTasks = deadLetterSupportPerformanceMonitorService.listHighPriorityRiskTasks(organizationId).stream()
                .map(reviewQueueService::toSummary)
                .limit(5)
                .toList();
        LocalDate today = LocalDate.now();
        Map<UUID, com.infinitematters.bookkeeping.workflows.WorkflowTask> openRiskTasksById = deadLetterSupportPerformanceMonitorService
                .listOpenRiskTasks(organizationId, com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceTaskFilter.ALL)
                .stream()
                .collect(Collectors.toMap(
                        com.infinitematters.bookkeeping.workflows.WorkflowTask::getId,
                        task -> task,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<UUID> reactivatedTasksNeedingAttention = new HashSet<>(openRiskTasksById.values().stream()
                .filter(task -> task.getAcknowledgedAt() == null)
                .map(com.infinitematters.bookkeeping.workflows.WorkflowTask::getId)
                .toList());
        long recentlyReactivatedCount = auditService.countRecentForOrganizationByEventType(
                organizationId,
                PERFORMANCE_REACTIVATED_EVENT_TYPE,
                Instant.now().minusSeconds(7 * 86400L));
        List<DashboardDeadLetterSupportPerformanceReactivationItem> recentReactivations = auditService
                .listRecentForOrganizationByEventType(organizationId, PERFORMANCE_REACTIVATED_EVENT_TYPE, 5)
                .stream()
                .map(event -> {
                    UUID taskId = event.entityId() != null ? UUID.fromString(event.entityId()) : null;
                    boolean needsAttention = taskId != null && reactivatedTasksNeedingAttention.contains(taskId);
                    boolean overdue = needsAttention
                            && taskId != null
                            && isOverdue(openRiskTasksById.get(taskId), today);
                    return new DashboardDeadLetterSupportPerformanceReactivationItem(
                        taskId,
                        event.details(),
                        event.createdAt(),
                        needsAttention,
                        overdue);
                })
                .toList();
        List<DashboardDeadLetterSupportPerformanceReactivationItem> recentReactivationsNeedingAttention = recentReactivations.stream()
                .filter(DashboardDeadLetterSupportPerformanceReactivationItem::needsAttention)
                .toList();
        long recentlyReactivatedNeedsAttentionCount = recentReactivations.stream()
                .filter(DashboardDeadLetterSupportPerformanceReactivationItem::needsAttention)
                .count();
        long freshlyReactivatedNeedsAttentionCount = recentReactivationsNeedingAttention.stream()
                .filter(item -> !item.overdue())
                .count();
        long reactivatedOverdueCount = recentReactivationsNeedingAttention.stream()
                .filter(DashboardDeadLetterSupportPerformanceReactivationItem::overdue)
                .count();
        Map<UUID, CategorizationDecision> latestDecisions = latestDecisionByTransaction(organizationId);
        List<DashboardExpenseCategorySummary> expenseCategories = expenseCategories(focusMonth, postedTransactions, latestDecisions);
        List<DashboardStaleAccountSummary> staleAccounts = staleAccounts(postedTransactions);
        List<ReconciliationSummary> reconciliationSummaries = reconciliationService.list(organizationId, focusMonth);
        List<DashboardStaleAccountSummary> unreconciledAccounts =
                unreconciledAccounts(organizationId, focusMonth, postedTransactions, reconciliationSummaries);
        DashboardPrimaryAction primaryAction = primaryAction(
                inbox,
                period,
                urgentRiskTasks.size(),
                urgentRiskTasks.isEmpty() ? null : "Review urgent support risks",
                urgentRiskTasks.isEmpty() ? null : "REVIEW_URGENT_SUPPORT_RISKS",
                urgentRiskTasks.isEmpty() ? null : "/workflows/notifications/dead-letter/performance/tasks/high-priority");

        return new DashboardSnapshot(
                focusMonth,
                cashBalance,
                postedTransactionCount,
                primaryAction,
                inbox,
                period,
                new DashboardNotificationHealthSnapshot(
                        notificationOperations.pendingCount(),
                        notificationOperations.failedCount(),
                        notificationOperations.bouncedCount(),
                        notificationOperations.deadLetterCount(),
                        notificationOperations.deadLetterOperations().acknowledgedCount(),
                        notificationOperations.deadLetterOperations().resolvedCount(),
                        notificationOperations.retryingCount(),
                        notificationOperations.suppressedDestinationCount(),
                        deadLetterQueue.needsRetry().size(),
                        deadLetterQueue.needsUnsuppress().size(),
                        new DashboardDeadLetterSupportTaskSnapshot(
                                supportTaskOperations.openCount(),
                                supportTaskOperations.unassignedCount(),
                                supportTaskOperations.overdueCount(),
                                supportTaskOperations.staleCount(),
                                supportTaskOperations.escalatedCount(),
                                supportTaskOperations.ignoredEscalationCount(),
                                supportTaskOperations.assignedAfterEscalationCount(),
                                supportTaskOperations.resolvedAfterEscalationCount(),
                                supportTaskOperations.oldestTasks()),
                        new DashboardDeadLetterEffectivenessSnapshot(
                                supportEffectiveness.weeks(),
                                supportEffectiveness.escalatedCount(),
                                supportEffectiveness.ignoredEscalationCount(),
                                supportEffectiveness.assignedAfterEscalationCount(),
                                supportEffectiveness.resolvedAfterEscalationCount(),
                                supportEffectiveness.buckets().stream()
                                        .map(bucket -> new DashboardDeadLetterEffectivenessBucket(
                                                bucket.weekStart(),
                                                bucket.weekEnd(),
                                                bucket.escalatedCount(),
                                                bucket.ignoredEscalationCount(),
                                                bucket.assignedAfterEscalationCount(),
                                                bucket.resolvedAfterEscalationCount()))
                                        .toList()),
                        new DashboardDeadLetterSupportPerformanceSnapshot(
                                "support-performance",
                                supportPerformance.weeks(),
                                supportPerformance.escalatedCount(),
                                supportPerformanceTaskStatus.openRiskTaskCount(),
                                supportPerformanceTaskStatus.acknowledgedRiskTaskCount(),
                                supportPerformanceTaskStatus.snoozedRiskTaskCount(),
                                supportPerformanceTaskStatus.ignoredRiskTaskCount(),
                                supportPerformanceTaskStatus.secondaryEscalationCount(),
                                recentlyReactivatedCount,
                                recentlyReactivatedNeedsAttentionCount,
                                freshlyReactivatedNeedsAttentionCount,
                                reactivatedOverdueCount,
                                supportPerformance.ignoredEscalationRate(),
                                supportPerformance.averageAssignmentLagHours(),
                                supportPerformance.averageResolutionLagHours(),
                                supportPerformance.ignoredEscalationRateBreached(),
                                supportPerformance.assignmentLagBreached(),
                                supportPerformance.resolutionLagBreached(),
                                supportPerformance.status(),
                                urgentRiskTasks.size(),
                                urgentRiskTasks.isEmpty() ? null : "Review urgent support risks",
                                urgentRiskTasks.isEmpty() ? null : "REVIEW_URGENT_SUPPORT_RISKS",
                                urgentRiskTasks.isEmpty() ? null : "/workflows/notifications/dead-letter/performance/tasks/high-priority",
                                urgentRiskTasks.isEmpty() ? null : DashboardActionUrgency.CRITICAL,
                                urgentRiskTasks,
                                recentReactivations,
                                recentReactivationsNeedingAttention),
                        topSupportActions(deadLetterQueue),
                        notificationOperations.deadLetterOperations().recentResolvedNotifications(),
                        notificationOperations.attentionNotifications().stream().limit(5).toList()),
                expenseCategories,
                staleAccounts,
                unreconciledAccounts,
                recentNotifications);
    }

    @Transactional(readOnly = true)
    public DashboardHomeSnapshot homeSnapshot(UUID organizationId, UUID currentUserId) {
        return homeSnapshot(
                organizationId,
                currentUserId,
                DashboardHomeContractVersion.defaultVersion().value(),
                false);
    }

    public DashboardHomeContractMetadata homeContractMetadata() {
        DashboardHomeContractVersion defaultVersion = DashboardHomeContractVersion.defaultVersion();
        DashboardHomeContractVersion latestVersion = java.util.Arrays.stream(DashboardHomeContractVersion.values())
                .max(java.util.Comparator.comparing(DashboardHomeContractVersion::value))
                .orElse(defaultVersion);
        List<DashboardHomeContractVersionMetadata> versions = java.util.Arrays.stream(DashboardHomeContractVersion.values())
                .map(version -> new DashboardHomeContractVersionMetadata(
                        version.value(),
                        version == defaultVersion,
                        version.notes(),
                        version.intendedUse(),
                        version.deprecated(),
                        version.deprecationDate(),
                        version.sunsetDate()))
                .toList();
        return new DashboardHomeContractMetadata(
                defaultVersion.value(),
                defaultVersion.value(),
                latestVersion.value(),
                HOME_CONTRACT_NEGOTIATION_POLICY,
                HOME_CONTRACT_HEADER_POLICY,
                DashboardHomeContractVersion.supportedVersionValues(),
                versions);
    }

    public DashboardHomeVersionsResponse homeVersionsResponse() {
        return new DashboardHomeVersionsResponse(homeContractMetadata());
    }

    @Transactional(readOnly = true)
    public DashboardHomeResponse homeResponse(UUID organizationId,
                                              UUID currentUserId,
                                              String requestedVersion,
                                              boolean explicitVersionRequested) {
        DashboardHomeContractMetadata metadata = homeContractMetadata();
        DashboardHomeContractNegotiation negotiation = DashboardHomeContractNegotiation.negotiate(
                requestedVersion,
                explicitVersionRequested);
        DashboardHomeSnapshot snapshot = homeSnapshot(organizationId, currentUserId, negotiation);
        return new DashboardHomeResponse(metadata, negotiation, snapshot);
    }

    @Transactional(readOnly = true)
    public DashboardHomeSnapshot homeSnapshot(UUID organizationId,
                                              UUID currentUserId,
                                              String requestedVersion) {
        return homeSnapshot(organizationId, currentUserId, requestedVersion, true);
    }

    @Transactional(readOnly = true)
    public DashboardHomeSnapshot homeSnapshot(UUID organizationId,
                                              UUID currentUserId,
                                              String requestedVersion,
                                              boolean explicitVersionRequested) {
        DashboardHomeContractNegotiation negotiation = DashboardHomeContractNegotiation.negotiate(requestedVersion,
                explicitVersionRequested);
        return homeSnapshot(organizationId, currentUserId, negotiation);
    }

    private DashboardHomeSnapshot homeSnapshot(UUID organizationId,
                                               UUID currentUserId,
                                               DashboardHomeContractNegotiation negotiation) {
        DashboardSnapshot snapshot = snapshot(organizationId, currentUserId);
        return new DashboardHomeSnapshot(
                negotiation.version().value(),
                negotiation.snapshot(),
                snapshot.focusMonth(),
                snapshot.cashBalance(),
                snapshot.postedTransactionCount(),
                snapshot.primaryAction(),
                snapshot.workflowInbox(),
                snapshot.period(),
                snapshot.notificationHealth().supportPerformance(),
                snapshot.expenseCategories(),
                snapshot.staleAccounts(),
                snapshot.recentNotifications());
    }

    private DashboardPrimaryAction primaryAction(WorkflowInboxSummary inbox,
                                                 DashboardPeriodSnapshot period,
                                                 long urgentRiskTaskCount,
                                                 String supportLabel,
                                                 String supportActionKey,
                                                 String supportActionPath) {
        if (supportLabel != null && supportActionKey != null && supportActionPath != null) {
            return new DashboardPrimaryAction(
                    "support-performance",
                    supportLabel,
                    supportActionKey,
                    supportActionPath,
                    urgentRiskTaskCount,
                    urgentRiskTaskCount == 1
                            ? "1 urgent support risk requires owner attention."
                            : urgentRiskTaskCount + " urgent support risks require owner attention.",
                    DashboardActionUrgency.CRITICAL,
                    "SUPPORT_PERFORMANCE");
        }
        if (period.recommendedActionLabel() != null
                && period.recommendedActionKey() != null
                && period.recommendedActionPath() != null) {
            return new DashboardPrimaryAction(
                    "period-close",
                    period.recommendedActionLabel(),
                    period.recommendedActionKey(),
                    period.recommendedActionPath(),
                    periodPrimaryCount(period),
                    periodPrimaryReason(period),
                    DashboardActionUrgency.HIGH,
                    "PERIOD_CLOSE");
        }
        if (inbox.recommendedActionLabel() != null
                && inbox.recommendedActionKey() != null
                && inbox.recommendedActionPath() != null) {
            return new DashboardPrimaryAction(
                    "workflow-inbox",
                    inbox.recommendedActionLabel(),
                    inbox.recommendedActionKey(),
                    inbox.recommendedActionPath(),
                    inboxPrimaryCount(inbox),
                    inboxPrimaryReason(inbox),
                    inboxPrimaryUrgency(inbox),
                    "WORKFLOW_INBOX");
        }
        return null;
    }

    private long inboxPrimaryCount(WorkflowInboxSummary inbox) {
        if ("PUSH_APPROVER_FOLLOW_THROUGH".equals(inbox.recommendedActionKey())
                || "FINISH_OVERRIDE_DOCUMENTATION".equals(inbox.recommendedActionKey())
                || "QUEUE_TOMORROWS_CLOSE_FOLLOW_UP".equals(inbox.recommendedActionKey())) {
            return 1;
        }
        if (inbox.overdueCount() > 0) {
            return inbox.overdueCount();
        }
        if (inbox.highPriorityCount() > 0) {
            return inbox.highPriorityCount();
        }
        return inbox.openCount();
    }

    private String inboxPrimaryReason(WorkflowInboxSummary inbox) {
        if ("PUSH_APPROVER_FOLLOW_THROUGH".equals(inbox.recommendedActionKey())) {
            return "A reviewed attestation escalation is now waiting on final approver follow-through.";
        }
        if ("FINISH_OVERRIDE_DOCUMENTATION".equals(inbox.recommendedActionKey())) {
            return "An override month is under review and still needs documentation before close can be treated as clean.";
        }
        if ("QUEUE_TOMORROWS_CLOSE_FOLLOW_UP".equals(inbox.recommendedActionKey())) {
            ReviewTaskSummary scheduledFollowUp = scheduledCloseControlFollowUp(inbox);
            if (scheduledFollowUp != null && scheduledFollowUp.dueDate() != null) {
                String month = extractMonthFromActionPath(scheduledFollowUp.actionPath());
                return "The close-control review for "
                        + (month != null ? month : "the focus month")
                        + " is intentionally paused until "
                        + scheduledFollowUp.dueDate().format(DASHBOARD_DATE_FORMAT)
                        + ".";
            }
            return "The close-control review is intentionally paused until the next scheduled follow-up window.";
        }
        if (inbox.overdueCount() > 0) {
            return inbox.overdueCount() == 1
                    ? "1 overdue bookkeeping task needs attention."
                    : inbox.overdueCount() + " overdue bookkeeping tasks need attention.";
        }
        if (inbox.highPriorityCount() > 0) {
            return inbox.highPriorityCount() == 1
                    ? "1 high-priority bookkeeping task is waiting."
                    : inbox.highPriorityCount() + " high-priority bookkeeping tasks are waiting.";
        }
        return inbox.openCount() == 1
                ? "1 bookkeeping task is still open."
                : inbox.openCount() + " bookkeeping tasks are still open.";
    }

    private DashboardActionUrgency inboxPrimaryUrgency(WorkflowInboxSummary inbox) {
        if (inbox.recommendedActionUrgency() != null) {
            return inbox.recommendedActionUrgency();
        }
        if (inbox.overdueCount() > 0 || inbox.highPriorityCount() > 0) {
            return DashboardActionUrgency.HIGH;
        }
        return DashboardActionUrgency.NORMAL;
    }

    private ReviewTaskSummary scheduledCloseControlFollowUp(WorkflowInboxSummary inbox) {
        return inbox.attentionTasks().stream()
                .filter(task -> "CLOSE_ATTESTATION_FOLLOW_UP".equals(task.taskType())
                        || "FORCE_CLOSE_REVIEW".equals(task.taskType()))
                .filter(task -> task.dueDate() != null)
                .findFirst()
                .orElse(null);
    }

    private String extractMonthFromActionPath(String actionPath) {
        if (actionPath == null) {
            return null;
        }
        int monthIndex = actionPath.indexOf("month=");
        if (monthIndex < 0) {
            return null;
        }
        return actionPath.substring(monthIndex + "month=".length());
    }

    private Long periodPrimaryCount(DashboardPeriodSnapshot period) {
        if ("FINISH_RECONCILIATIONS".equals(period.recommendedActionKey())) {
            return (long) period.unreconciledAccountCount();
        }
        return null;
    }

    private String periodPrimaryReason(DashboardPeriodSnapshot period) {
        if ("FINISH_RECONCILIATIONS".equals(period.recommendedActionKey())) {
            return period.unreconciledAccountCount() == 1
                    ? "1 unreconciled account is blocking period close."
                    : period.unreconciledAccountCount() + " unreconciled accounts are blocking period close.";
        }
        return "The current period still has unresolved close blockers.";
    }

    private Map<UUID, CategorizationDecision> latestDecisionByTransaction(UUID organizationId) {
        return categorizationDecisionRepository.findByTransactionOrganizationId(organizationId).stream()
                .collect(Collectors.toMap(
                        decision -> decision.getTransaction().getId(),
                        decision -> decision,
                        (left, right) -> left.getCreatedAt().isAfter(right.getCreatedAt()) ? left : right,
                        LinkedHashMap::new));
    }

    private List<DashboardExpenseCategorySummary> expenseCategories(YearMonth focusMonth,
                                                                    List<BookkeepingTransaction> postedTransactions,
                                                                    Map<UUID, CategorizationDecision> latestDecisions) {
        Map<Category, BigDecimal> current = expenseTotalsForMonth(focusMonth, postedTransactions, latestDecisions);
        Map<Category, BigDecimal> previous = expenseTotalsForMonth(focusMonth.minusMonths(1), postedTransactions, latestDecisions);

        return current.entrySet().stream()
                .sorted(Map.Entry.<Category, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> new DashboardExpenseCategorySummary(
                        "expense-category-" + entry.getKey().name().toLowerCase(),
                        entry.getKey(),
                        entry.getValue(),
                        entry.getValue().subtract(previous.getOrDefault(entry.getKey(), BigDecimal.ZERO)),
                        "REVIEW_EXPENSE_CATEGORY",
                        "/transactions?category=" + entry.getKey().name(),
                        expenseCategoryUrgency(entry.getValue().subtract(previous.getOrDefault(entry.getKey(), BigDecimal.ZERO))),
                        expenseCategoryReason(entry.getValue().subtract(previous.getOrDefault(entry.getKey(), BigDecimal.ZERO)))))
                .toList();
    }

    private Map<Category, BigDecimal> expenseTotalsForMonth(YearMonth month,
                                                            List<BookkeepingTransaction> postedTransactions,
                                                            Map<UUID, CategorizationDecision> latestDecisions) {
        return postedTransactions.stream()
                .filter(transaction -> YearMonth.from(transaction.getTransactionDate()).equals(month))
                .map(transaction -> Map.entry(transaction, latestDecisions.get(transaction.getId())))
                .filter(entry -> entry.getValue() != null)
                .map(entry -> Map.entry(resolveCategory(entry.getValue()), entry.getKey().getAmount().abs()))
                .filter(entry -> isExpenseCategory(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        BigDecimal::add));
    }

    private List<DashboardStaleAccountSummary> staleAccounts(List<BookkeepingTransaction> postedTransactions) {
        LocalDate today = LocalDate.now();
        Map<UUID, BookkeepingTransaction> latestByAccount = postedTransactions.stream()
                .collect(Collectors.toMap(
                        transaction -> transaction.getFinancialAccount().getId(),
                        transaction -> transaction,
                        (left, right) -> left.getTransactionDate().isAfter(right.getTransactionDate()) ? left : right));

        return latestByAccount.values().stream()
                .map(transaction -> toStaleAccountSummary(transaction.getFinancialAccount(), transaction.getTransactionDate(), today))
                .filter(summary -> summary.daysSinceActivity() >= STALE_ACCOUNT_DAYS)
                .sorted(Comparator.comparingLong(DashboardStaleAccountSummary::daysSinceActivity).reversed())
                .toList();
    }

    private List<DashboardStaleAccountSummary> unreconciledAccounts(UUID organizationId,
                                                                    YearMonth month,
                                                                    List<BookkeepingTransaction> postedTransactions,
                                                                    List<ReconciliationSummary> reconciliationSummaries) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        LocalDate today = LocalDate.now();

        Map<UUID, BookkeepingTransaction> latestPostedTransactionByAccount = postedTransactions.stream()
                .filter(transaction -> !transaction.getTransactionDate().isBefore(start))
                .filter(transaction -> !transaction.getTransactionDate().isAfter(end))
                .collect(Collectors.toMap(
                        transaction -> transaction.getFinancialAccount().getId(),
                        transaction -> transaction,
                        (left, right) -> left.getTransactionDate().isAfter(right.getTransactionDate()) ? left : right));

        Map<UUID, ReconciliationSummary> reconciliationByAccount = reconciliationSummaries.stream()
                .collect(Collectors.toMap(
                        ReconciliationSummary::financialAccountId,
                        summary -> summary,
                        (left, right) -> left.createdAt().isAfter(right.createdAt()) ? left : right,
                        LinkedHashMap::new));

        return financialAccountRepository.findByOrganizationId(organizationId).stream()
                .filter(FinancialAccount::isActive)
                .filter(account -> journalEntryRepository.countTransactionEntriesForAccountInPeriod(account.getId(), start, end) > 0)
                .filter(account -> {
                    ReconciliationSummary reconciliation = reconciliationByAccount.get(account.getId());
                    return reconciliation == null || reconciliation.status() != ReconciliationStatus.COMPLETED;
                })
                .map(account -> {
                    ReconciliationSummary reconciliation = reconciliationByAccount.get(account.getId());
                    LocalDate lastTransactionDate = latestPostedTransactionByAccount.containsKey(account.getId())
                            ? latestPostedTransactionByAccount.get(account.getId()).getTransactionDate()
                            : end;
                    long daysSinceActivity = ChronoUnit.DAYS.between(lastTransactionDate, today);
                    String itemId = reconciliation != null ? "recon-" + reconciliation.id() : "recon-account-" + account.getId();
                    String actionPath = "/reconciliation/" + account.getId() + "?month=" + month;
                    String actionReason = reconciliation != null
                            ? "Reconciliation is in progress for this account."
                            : "Account requires reconciliation before period close.";

                    return new DashboardStaleAccountSummary(
                            itemId,
                            account.getId(),
                            account.getName(),
                            account.getAccountType(),
                            lastTransactionDate,
                            daysSinceActivity,
                            "REVIEW_RECONCILIATION",
                            actionPath,
                            DashboardActionUrgency.HIGH,
                            actionReason,
                            reconciliation != null
                    );
                })
                .toList();
    }

    private boolean isOverdue(com.infinitematters.bookkeeping.workflows.WorkflowTask task, LocalDate today) {
        return task != null
                && task.getDueDate() != null
                && task.getDueDate().isBefore(today);
    }

    private DashboardStaleAccountSummary toStaleAccountSummary(FinancialAccount account, LocalDate lastTransactionDate, LocalDate today) {
        return new DashboardStaleAccountSummary(
                "stale-account-" + account.getId(),
                account.getId(),
                account.getName(),
                account.getAccountType(),
                lastTransactionDate,
                ChronoUnit.DAYS.between(lastTransactionDate, today),
                "REVIEW_STALE_ACCOUNT",
                "/reconciliation?accountId=" + account.getId(),
                staleAccountUrgency(ChronoUnit.DAYS.between(lastTransactionDate, today)),
                staleAccountReason(ChronoUnit.DAYS.between(lastTransactionDate, today)),
                false);
    }

    private DashboardActionUrgency expenseCategoryUrgency(BigDecimal deltaFromPreviousMonth) {
        return deltaFromPreviousMonth.abs().compareTo(EXPENSE_CATEGORY_HIGH_URGENCY_DELTA) >= 0
                ? DashboardActionUrgency.HIGH
                : DashboardActionUrgency.NORMAL;
    }

    private String expenseCategoryReason(BigDecimal deltaFromPreviousMonth) {
        if (deltaFromPreviousMonth.compareTo(BigDecimal.ZERO) > 0) {
            return "Up " + deltaFromPreviousMonth.abs() + " from last month.";
        }
        if (deltaFromPreviousMonth.compareTo(BigDecimal.ZERO) < 0) {
            return "Down " + deltaFromPreviousMonth.abs() + " from last month.";
        }
        return "Flat versus last month.";
    }

    private DashboardActionUrgency staleAccountUrgency(long daysSinceActivity) {
        return daysSinceActivity >= STALE_ACCOUNT_HIGH_URGENCY_DAYS
                ? DashboardActionUrgency.HIGH
                : DashboardActionUrgency.NORMAL;
    }

    private String staleAccountReason(long daysSinceActivity) {
        return daysSinceActivity == 1
                ? "No activity for 1 day."
                : "No activity for " + daysSinceActivity + " days.";
    }

    private Category resolveCategory(CategorizationDecision decision) {
        return decision.getFinalCategory() != null ? decision.getFinalCategory() : decision.getProposedCategory();
    }

    private boolean isExpenseCategory(Category category) {
        return category != Category.INCOME && category != Category.TRANSFER;
    }

    private List<DashboardDeadLetterActionSummary> topSupportActions(DeadLetterQueueSummary deadLetterQueue) {
        return List.of(
                new DashboardDeadLetterActionSummary(
                        DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY,
                        deadLetterQueue.needsUnsuppress().size(),
                        "Unsuppress and retry"),
                new DashboardDeadLetterActionSummary(
                        DeadLetterRecommendedAction.RETRY_DELIVERY,
                        deadLetterQueue.needsRetry().size(),
                        "Retry delivery"),
                new DashboardDeadLetterActionSummary(
                        DeadLetterRecommendedAction.REVIEW_ACKNOWLEDGED,
                        deadLetterQueue.acknowledged().size(),
                        "Review acknowledged"))
                .stream()
                .filter(summary -> summary.count() > 0)
                .toList();
    }

    private String recommendedPeriodAction(boolean closeReady, int unreconciledCount) {
        if (unreconciledCount > 0) {
            return "Finish account reconciliations";
        }
        if (!closeReady) {
            return "Resolve remaining close blockers";
        }
        return null;
    }

    private String recommendedPeriodActionKey(boolean closeReady, int unreconciledCount) {
        if (unreconciledCount > 0) {
            return "FINISH_RECONCILIATIONS";
        }
        if (!closeReady) {
            return "RESOLVE_CLOSE_BLOCKERS";
        }
        return null;
    }

    private String recommendedPeriodActionPath(boolean closeReady, int unreconciledCount) {
        if (unreconciledCount > 0) {
            return "/reconciliation";
        }
        if (!closeReady) {
            return "/periods/checklist";
        }
        return null;
    }

    private DashboardActionUrgency recommendedPeriodActionUrgency(boolean closeReady, int unreconciledCount) {
        if (unreconciledCount > 0 || !closeReady) {
            return DashboardActionUrgency.HIGH;
        }
        return null;
    }
}
