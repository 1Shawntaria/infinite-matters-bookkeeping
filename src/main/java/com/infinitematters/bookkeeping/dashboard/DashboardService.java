package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.accounts.AccountType;
import com.infinitematters.bookkeeping.accounts.FinancialAccount;
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
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportTaskOperationsSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterWorkflowTaskService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.periods.AccountingPeriod;
import com.infinitematters.bookkeeping.periods.AccountingPeriodRepository;
import com.infinitematters.bookkeeping.transactions.BookkeepingTransaction;
import com.infinitematters.bookkeeping.transactions.BookkeepingTransactionRepository;
import com.infinitematters.bookkeeping.transactions.CategorizationDecision;
import com.infinitematters.bookkeeping.transactions.CategorizationDecisionRepository;
import com.infinitematters.bookkeeping.transactions.TransactionStatus;
import com.infinitematters.bookkeeping.workflows.ReviewQueueService;
import com.infinitematters.bookkeeping.workflows.WorkflowInboxSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    private static final long STALE_ACCOUNT_DAYS = 30;

    private final OrganizationService organizationService;
    private final BookkeepingTransactionRepository transactionRepository;
    private final CategorizationDecisionRepository categorizationDecisionRepository;
    private final ReviewQueueService reviewQueueService;
    private final NotificationService notificationService;
    private final DeadLetterWorkflowTaskService deadLetterWorkflowTaskService;
    private final CloseChecklistService closeChecklistService;
    private final AccountingPeriodRepository accountingPeriodRepository;

    public DashboardService(OrganizationService organizationService,
                            BookkeepingTransactionRepository transactionRepository,
                            CategorizationDecisionRepository categorizationDecisionRepository,
                            ReviewQueueService reviewQueueService,
                            NotificationService notificationService,
                            DeadLetterWorkflowTaskService deadLetterWorkflowTaskService,
                            CloseChecklistService closeChecklistService,
                            AccountingPeriodRepository accountingPeriodRepository) {
        this.organizationService = organizationService;
        this.transactionRepository = transactionRepository;
        this.categorizationDecisionRepository = categorizationDecisionRepository;
        this.reviewQueueService = reviewQueueService;
        this.notificationService = notificationService;
        this.deadLetterWorkflowTaskService = deadLetterWorkflowTaskService;
        this.closeChecklistService = closeChecklistService;
        this.accountingPeriodRepository = accountingPeriodRepository;
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
                checklist.closeReady(),
                unreconciledCount,
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
        Map<UUID, CategorizationDecision> latestDecisions = latestDecisionByTransaction(organizationId);
        List<DashboardExpenseCategorySummary> expenseCategories = expenseCategories(focusMonth, postedTransactions, latestDecisions);
        List<DashboardStaleAccountSummary> staleAccounts = staleAccounts(postedTransactions);

        return new DashboardSnapshot(
                focusMonth,
                cashBalance,
                postedTransactionCount,
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
                                supportPerformance.weeks(),
                                supportPerformance.escalatedCount(),
                                supportPerformance.ignoredEscalationRate(),
                                supportPerformance.averageAssignmentLagHours(),
                                supportPerformance.averageResolutionLagHours(),
                                supportPerformance.ignoredEscalationRateBreached(),
                                supportPerformance.assignmentLagBreached(),
                                supportPerformance.resolutionLagBreached(),
                                supportPerformance.status()),
                        topSupportActions(deadLetterQueue),
                        notificationOperations.deadLetterOperations().recentResolvedNotifications(),
                        notificationOperations.attentionNotifications().stream().limit(5).toList()),
                expenseCategories,
                staleAccounts,
                recentNotifications);
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
                        entry.getKey(),
                        entry.getValue(),
                        entry.getValue().subtract(previous.getOrDefault(entry.getKey(), BigDecimal.ZERO))))
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

    private DashboardStaleAccountSummary toStaleAccountSummary(FinancialAccount account, LocalDate lastTransactionDate, LocalDate today) {
        return new DashboardStaleAccountSummary(
                account.getId(),
                account.getName(),
                account.getAccountType(),
                lastTransactionDate,
                ChronoUnit.DAYS.between(lastTransactionDate, today));
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
}
