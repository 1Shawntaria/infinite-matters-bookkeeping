package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.accounts.AccountType;
import com.infinitematters.bookkeeping.accounts.FinancialAccount;
import com.infinitematters.bookkeeping.close.CloseChecklistItem;
import com.infinitematters.bookkeeping.close.CloseChecklistService;
import com.infinitematters.bookkeeping.close.CloseChecklistSummary;
import com.infinitematters.bookkeeping.domain.Category;
import com.infinitematters.bookkeeping.notifications.NotificationService;
import com.infinitematters.bookkeeping.notifications.NotificationSummary;
import com.infinitematters.bookkeeping.notifications.NotificationCategory;
import com.infinitematters.bookkeeping.notifications.NotificationChannel;
import com.infinitematters.bookkeeping.notifications.NotificationDeliveryState;
import com.infinitematters.bookkeeping.notifications.NotificationStatus;
import com.infinitematters.bookkeeping.notifications.DeadLetterQueueItem;
import com.infinitematters.bookkeeping.notifications.DeadLetterQueueSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterRecommendedAction;
import com.infinitematters.bookkeeping.notifications.DeadLetterResolutionStatus;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportEffectivenessBucket;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportEffectivenessSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceStatus;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportTaskOperationsSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportTaskSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterWorkflowTaskService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.periods.AccountingPeriod;
import com.infinitematters.bookkeeping.periods.AccountingPeriodRepository;
import com.infinitematters.bookkeeping.periods.AccountingPeriodStatus;
import com.infinitematters.bookkeeping.periods.PeriodCloseMethod;
import com.infinitematters.bookkeeping.transactions.BookkeepingTransaction;
import com.infinitematters.bookkeeping.transactions.BookkeepingTransactionRepository;
import com.infinitematters.bookkeeping.transactions.CategorizationDecision;
import com.infinitematters.bookkeeping.transactions.CategorizationDecisionRepository;
import com.infinitematters.bookkeeping.transactions.TransactionStatus;
import com.infinitematters.bookkeeping.workflows.WorkflowInboxSummary;
import com.infinitematters.bookkeeping.workflows.ReviewQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTests {

    @Mock
    private OrganizationService organizationService;
    @Mock
    private BookkeepingTransactionRepository transactionRepository;
    @Mock
    private CategorizationDecisionRepository categorizationDecisionRepository;
    @Mock
    private ReviewQueueService reviewQueueService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private DeadLetterWorkflowTaskService deadLetterWorkflowTaskService;
    @Mock
    private CloseChecklistService closeChecklistService;
    @Mock
    private AccountingPeriodRepository accountingPeriodRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                organizationService,
                transactionRepository,
                categorizationDecisionRepository,
                reviewQueueService,
                notificationService,
                deadLetterWorkflowTaskService,
                closeChecklistService,
                accountingPeriodRepository);
    }

    @Test
    void snapshotIncludesExpenseTrendsAndStaleAccounts() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID bankAccountId = UUID.randomUUID();
        UUID staleAccountId = UUID.randomUUID();

        FinancialAccount account = new FinancialAccount();
        setField(account, "id", bankAccountId);
        account.setName("Operating Checking");
        account.setAccountType(AccountType.BANK);

        FinancialAccount staleAccount = new FinancialAccount();
        setField(staleAccount, "id", staleAccountId);
        staleAccount.setName("Reserve Checking");
        staleAccount.setAccountType(AccountType.BANK);

        BookkeepingTransaction marchSoftware = transaction(bankAccountId, account, LocalDate.of(2026, 3, 20), "60.00");
        BookkeepingTransaction febSoftware = transaction(bankAccountId, account, LocalDate.of(2026, 2, 20), "40.00");
        BookkeepingTransaction staleMeals = transaction(staleAccountId, staleAccount, LocalDate.now().minusDays(45), "20.00");

        setField(marchSoftware, "id", UUID.randomUUID());
        setField(febSoftware, "id", UUID.randomUUID());
        setField(staleMeals, "id", UUID.randomUUID());

        when(transactionRepository.findFirstByOrganizationIdOrderByTransactionDateDescCreatedAtDesc(organizationId))
                .thenReturn(Optional.of(marchSoftware));
        when(transactionRepository.sumAmountsByOrganizationAndStatusAndAccountTypes(
                organizationId, TransactionStatus.POSTED, List.of(AccountType.BANK, AccountType.CASH)))
                .thenReturn(new BigDecimal("120.00"));
        when(transactionRepository.findByOrganizationIdAndStatusOrderByTransactionDateDescCreatedAtDesc(organizationId, TransactionStatus.POSTED))
                .thenReturn(List.of(marchSoftware, febSoftware, staleMeals));
        when(categorizationDecisionRepository.findByTransactionOrganizationId(organizationId))
                .thenReturn(List.of(
                        decision(marchSoftware, Category.SOFTWARE),
                        decision(febSoftware, Category.SOFTWARE),
                        decision(staleMeals, Category.MEALS)));
        when(reviewQueueService.inbox(organizationId, userId))
                .thenReturn(new WorkflowInboxSummary(1, 0, 0, 1, 0, 1, List.of()));
        when(closeChecklistService.checklist(organizationId, YearMonth.of(2026, 3)))
                .thenReturn(new CloseChecklistSummary(
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31),
                        false,
                        List.of(
                                new CloseChecklistItem("OPEN_REVIEW_TASKS", "Tasks resolved", true, "ok"),
                                new CloseChecklistItem("ACCOUNT_RECONCILIATION", "Operating Checking reconciled", false, "pending"))));

        AccountingPeriod period = new AccountingPeriod();
        period.setStatus(AccountingPeriodStatus.CLOSED);
        period.setCloseMethod(PeriodCloseMethod.OVERRIDE);
        period.setOverrideReason("Approved variance");
        when(accountingPeriodRepository.findFirstByOrganizationIdOrderByPeriodEndDesc(organizationId))
                .thenReturn(Optional.of(period));
        when(notificationService.listForOrganization(organizationId))
                .thenReturn(List.of(new NotificationSummary(
                        UUID.randomUUID(), UUID.randomUUID(), userId, NotificationCategory.WORKFLOW, NotificationChannel.IN_APP,
                        NotificationStatus.SENT, NotificationDeliveryState.DELIVERED, "msg", "workflow_task", UUID.randomUUID().toString(),
                        "member@acme.test", null, null, 0, null, null, null, null, null, null, null, Instant.now(), Instant.now(), Instant.now(), Instant.now())));
        when(notificationService.operationsSummary(organizationId))
                .thenReturn(new com.infinitematters.bookkeeping.notifications.NotificationOperationsSummary(
                        1,
                        1,
                        1,
                        1,
                        1,
                        3,
                        List.of(new NotificationSummary(
                                UUID.randomUUID(), UUID.randomUUID(), userId, NotificationCategory.WORKFLOW, NotificationChannel.EMAIL,
                                NotificationStatus.FAILED, NotificationDeliveryState.FAILED, "failed", "workflow_task", UUID.randomUUID().toString(),
                                "member@acme.test", "test-provider", "provider-message-1", 3, "provider unavailable", "PROVIDER_REJECTED",
                                DeadLetterResolutionStatus.OPEN, null, "Investigating", Instant.now(), userId,
                                Instant.now(), Instant.now(), null, Instant.now())),
                        new com.infinitematters.bookkeeping.notifications.DeadLetterOperationsSummary(
                                1,
                                1,
                                1,
                                List.of(new NotificationSummary(
                                        UUID.randomUUID(), UUID.randomUUID(), userId, NotificationCategory.WORKFLOW, NotificationChannel.EMAIL,
                                        NotificationStatus.FAILED, NotificationDeliveryState.FAILED, "failed", "workflow_task", UUID.randomUUID().toString(),
                                        "member@acme.test", "test-provider", "provider-message-2", 3, "resolved", "PROVIDER_REJECTED",
                                        DeadLetterResolutionStatus.RESOLVED, com.infinitematters.bookkeeping.notifications.DeadLetterResolutionReasonCode.DELIVERY_NO_LONGER_REQUIRED, "Handled", Instant.now(), userId,
                                        Instant.now(), Instant.now(), null, Instant.now())))));
        when(notificationService.deadLetterQueue(organizationId))
                .thenReturn(new DeadLetterQueueSummary(
                        List.of(new DeadLetterQueueItem(
                                new NotificationSummary(
                                        UUID.randomUUID(), UUID.randomUUID(), userId, NotificationCategory.WORKFLOW, NotificationChannel.EMAIL,
                                        NotificationStatus.FAILED, NotificationDeliveryState.FAILED, "retry", "workflow_task", UUID.randomUUID().toString(),
                                        "member@acme.test", "test-provider", "provider-message-3", 1, "provider unavailable", "PROVIDER_REJECTED",
                                        DeadLetterResolutionStatus.OPEN, null, "Retry recommended", Instant.now(), userId,
                                        Instant.now(), Instant.now(), null, Instant.now()),
                                DeadLetterRecommendedAction.RETRY_DELIVERY,
                                false,
                                null,
                                "Delivery can be retried after reviewing the destination")),
                        List.of(new DeadLetterQueueItem(
                                new NotificationSummary(
                                        UUID.randomUUID(), UUID.randomUUID(), userId, NotificationCategory.WORKFLOW, NotificationChannel.EMAIL,
                                        NotificationStatus.FAILED, NotificationDeliveryState.FAILED, "suppressed", "workflow_task", UUID.randomUUID().toString(),
                                        "member@acme.test", "sendgrid", "provider-message-4", 2, "recipient suppressed", "RECIPIENT_SUPPRESSED",
                                        DeadLetterResolutionStatus.OPEN, null, "Unsuppress first", Instant.now(), userId,
                                        Instant.now(), Instant.now(), null, Instant.now()),
                                DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY,
                                true,
                                null,
                                "Recipient is currently suppressed by the provider")),
                        List.of(new DeadLetterQueueItem(
                                new NotificationSummary(
                                        UUID.randomUUID(), UUID.randomUUID(), userId, NotificationCategory.WORKFLOW, NotificationChannel.EMAIL,
                                        NotificationStatus.FAILED, NotificationDeliveryState.FAILED, "ack", "workflow_task", UUID.randomUUID().toString(),
                                        "member@acme.test", "test-provider", "provider-message-5", 1, "acknowledged", "PROVIDER_REJECTED",
                                        DeadLetterResolutionStatus.ACKNOWLEDGED, null, "Waiting", Instant.now(), userId,
                                        Instant.now(), Instant.now(), null, Instant.now()),
                                DeadLetterRecommendedAction.REVIEW_ACKNOWLEDGED,
                                false,
                                null,
                                "Acknowledged and awaiting operator follow-up")),
                        List.of()));
        when(deadLetterWorkflowTaskService.operationsSummary(organizationId))
                .thenReturn(new DeadLetterSupportTaskOperationsSummary(
                        2,
                        1,
                        1,
                        1,
                        1,
                        1,
                        1,
                        1,
                        List.of(
                                new DeadLetterSupportTaskSummary(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY,
                                        "Recipient is currently suppressed by the provider",
                                        "CRITICAL",
                                        true,
                                        true,
                                        true,
                                        true,
                                        false,
                                        4,
                                        1,
                                        Instant.now().minusSeconds(3600),
                                        LocalDate.now().minusDays(1),
                                        userId,
                                        "Ops Owner",
                                        "member@acme.test",
                                        "Unsuppress and retry failed notification",
                                        "Recipient is suppressed and must be unsuppressed before retrying delivery."),
                                new DeadLetterSupportTaskSummary(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        DeadLetterRecommendedAction.RETRY_DELIVERY,
                                        "Delivery can be retried after reviewing the destination",
                                        "HIGH",
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        1,
                                        0,
                                        null,
                                        LocalDate.now().plusDays(1),
                                        null,
                                        null,
                                        "member@acme.test",
                                        "Retry failed notification delivery",
                                        "Delivery can be retried after reviewing the recipient and provider failure details."))));
        when(deadLetterWorkflowTaskService.effectivenessSummary(organizationId, 6))
                .thenReturn(new DeadLetterSupportEffectivenessSummary(
                        LocalDate.of(2026, 2, 23),
                        LocalDate.of(2026, 4, 5),
                        6,
                        4,
                        1,
                        2,
                        1,
                        List.of(
                                new DeadLetterSupportEffectivenessBucket(
                                        LocalDate.of(2026, 3, 23),
                                        LocalDate.of(2026, 3, 29),
                                        1,
                                        0,
                                        1,
                                        0),
                                new DeadLetterSupportEffectivenessBucket(
                                        LocalDate.of(2026, 3, 30),
                                        LocalDate.of(2026, 4, 5),
                                        2,
                                        1,
                                        0,
                                        1))));
        when(deadLetterWorkflowTaskService.performanceSummary(organizationId, 6))
                .thenReturn(new DeadLetterSupportPerformanceSummary(
                        LocalDate.of(2026, 2, 23),
                        LocalDate.of(2026, 4, 5),
                        6,
                        4,
                        0.25,
                        18.0,
                        54.0,
                        false,
                        false,
                        true,
                        DeadLetterSupportPerformanceStatus.AT_RISK));

        DashboardSnapshot snapshot = dashboardService.snapshot(organizationId, userId);

        assertThat(snapshot.focusMonth()).isEqualTo(YearMonth.of(2026, 3));
        assertThat(snapshot.cashBalance()).isEqualByComparingTo("120.00");
        assertThat(snapshot.expenseCategories()).isNotEmpty();
        assertThat(snapshot.expenseCategories().get(0).category()).isEqualTo(Category.SOFTWARE);
        assertThat(snapshot.expenseCategories().get(0).amount()).isEqualByComparingTo("60.00");
        assertThat(snapshot.expenseCategories().get(0).deltaFromPreviousMonth()).isEqualByComparingTo("20.00");
        assertThat(snapshot.staleAccounts()).hasSize(1);
        assertThat(snapshot.staleAccounts().get(0).accountName()).isEqualTo("Reserve Checking");
        assertThat(snapshot.period().latestCloseMethod()).isEqualTo(PeriodCloseMethod.OVERRIDE);
        assertThat(snapshot.period().unreconciledAccountCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().pendingCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().failedCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().bouncedCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().deadLetterCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().acknowledgedDeadLetterCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().resolvedDeadLetterCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().retryingCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().suppressedDestinationCount()).isEqualTo(3);
        assertThat(snapshot.notificationHealth().needsRetryCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().needsUnsuppressCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportTasks().openCount()).isEqualTo(2);
        assertThat(snapshot.notificationHealth().supportTasks().unassignedCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportTasks().overdueCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportTasks().staleCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportTasks().escalatedCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportTasks().ignoredEscalationCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportTasks().assignedAfterEscalationCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportTasks().resolvedAfterEscalationCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportTasks().oldestTasks()).hasSize(2);
        assertThat(snapshot.notificationHealth().supportTasks().oldestTasks().get(0).recommendedAction())
                .isEqualTo(DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY);
        assertThat(snapshot.notificationHealth().supportTasks().oldestTasks().get(0).ignoredEscalation()).isTrue();
        assertThat(snapshot.notificationHealth().supportTasks().oldestTasks().get(0).assignedAfterEscalation()).isTrue();
        assertThat(snapshot.notificationHealth().supportEffectiveness().weeks()).isEqualTo(6);
        assertThat(snapshot.notificationHealth().supportEffectiveness().escalatedCount()).isEqualTo(4);
        assertThat(snapshot.notificationHealth().supportEffectiveness().ignoredEscalationCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportEffectiveness().assignedAfterEscalationCount()).isEqualTo(2);
        assertThat(snapshot.notificationHealth().supportEffectiveness().resolvedAfterEscalationCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportEffectiveness().recentWeeks()).hasSize(2);
        assertThat(snapshot.notificationHealth().supportEffectiveness().recentWeeks().get(0).weekStart())
                .isEqualTo(LocalDate.of(2026, 3, 23));
        assertThat(snapshot.notificationHealth().supportEffectiveness().recentWeeks().get(1).ignoredEscalationCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportPerformance().weeks()).isEqualTo(6);
        assertThat(snapshot.notificationHealth().supportPerformance().ignoredEscalationRate()).isEqualTo(0.25);
        assertThat(snapshot.notificationHealth().supportPerformance().averageAssignmentLagHours()).isEqualTo(18.0);
        assertThat(snapshot.notificationHealth().supportPerformance().averageResolutionLagHours()).isEqualTo(54.0);
        assertThat(snapshot.notificationHealth().supportPerformance().ignoredEscalationRateBreached()).isFalse();
        assertThat(snapshot.notificationHealth().supportPerformance().assignmentLagBreached()).isFalse();
        assertThat(snapshot.notificationHealth().supportPerformance().resolutionLagBreached()).isTrue();
        assertThat(snapshot.notificationHealth().supportPerformance().status()).isEqualTo(DeadLetterSupportPerformanceStatus.AT_RISK);
        assertThat(snapshot.notificationHealth().topSupportActions()).hasSize(3);
        assertThat(snapshot.notificationHealth().topSupportActions().get(0).action()).isEqualTo(DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY);
        assertThat(snapshot.notificationHealth().recentResolvedDeadLetters()).hasSize(1);
        assertThat(snapshot.notificationHealth().attentionNotifications()).hasSize(1);
    }

    private BookkeepingTransaction transaction(UUID organizationId, FinancialAccount account, LocalDate date, String amount) {
        BookkeepingTransaction transaction = new BookkeepingTransaction();
        transaction.setFinancialAccount(account);
        transaction.setTransactionDate(date);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setStatus(TransactionStatus.POSTED);
        return transaction;
    }

    private CategorizationDecision decision(BookkeepingTransaction transaction, Category category) {
        CategorizationDecision decision = new CategorizationDecision();
        decision.setTransaction(transaction);
        decision.setProposedCategory(category);
        decision.setFinalCategory(category);
        return decision;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
