package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.accounts.AccountType;
import com.infinitematters.bookkeeping.accounts.FinancialAccount;
import com.infinitematters.bookkeeping.audit.AuditEventSummary;
import com.infinitematters.bookkeeping.audit.AuditService;
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
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceMonitorService;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceTaskFilter;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceTaskStatus;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportTaskOperationsSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportTaskSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterWorkflowTaskService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.periods.AccountingPeriod;
import com.infinitematters.bookkeeping.periods.AccountingPeriodRepository;
import com.infinitematters.bookkeeping.periods.AccountingPeriodStatus;
import com.infinitematters.bookkeeping.periods.PeriodCloseMethod;
import com.infinitematters.bookkeeping.reconciliation.ReconciliationService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private DeadLetterSupportPerformanceMonitorService deadLetterSupportPerformanceMonitorService;
    @Mock
    private CloseChecklistService closeChecklistService;
    @Mock
    private AccountingPeriodRepository accountingPeriodRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private ReconciliationService reconciliationService;

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
                deadLetterSupportPerformanceMonitorService,
                closeChecklistService,
                accountingPeriodRepository,
                auditService,
                reconciliationService);
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

        BookkeepingTransaction marchSoftware = transaction(bankAccountId, account, LocalDate.now().minusDays(29), "60.00");
        BookkeepingTransaction febSoftware = transaction(bankAccountId, account, LocalDate.now().minusDays(60), "40.00");
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
                .thenReturn(new WorkflowInboxSummary(
                        "workflow-inbox",
                        1,
                        0,
                        0,
                        1,
                        0,
                        1,
                        "Review high-priority bookkeeping tasks",
                        "REVIEW_HIGH_PRIORITY_TASKS",
                        "/workflows/inbox",
                        DashboardActionUrgency.HIGH,
                        List.of()));
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
        when(deadLetterSupportPerformanceMonitorService.taskStatus(organizationId))
                .thenReturn(new DeadLetterSupportPerformanceTaskStatus(1, 0, 0, 1, 2));
        com.infinitematters.bookkeeping.workflows.WorkflowTask openRiskTask =
                workflowTask(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(deadLetterSupportPerformanceMonitorService.listOpenRiskTasks(
                organizationId,
                DeadLetterSupportPerformanceTaskFilter.ALL))
                .thenReturn(List.of(openRiskTask));
        com.infinitematters.bookkeeping.workflows.WorkflowTask urgentIgnoredTask =
                workflowTask(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        com.infinitematters.bookkeeping.workflows.WorkflowTask urgentReactivatedTask =
                workflowTask(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        when(deadLetterSupportPerformanceMonitorService.listHighPriorityRiskTasks(organizationId))
                .thenReturn(List.of(urgentReactivatedTask, urgentIgnoredTask));
        when(reviewQueueService.toSummary(urgentReactivatedTask))
                .thenReturn(new com.infinitematters.bookkeeping.workflows.ReviewTaskSummary(
                        urgentReactivatedTask.getId(),
                        null,
                        null,
                        "DEAD_LETTER_SUPPORT_PERFORMANCE",
                        "CRITICAL",
                        true,
                        "Reactivated overdue performance risk",
                        "Needs immediate owner attention",
                        LocalDate.now().minusDays(1),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0.0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
        when(reviewQueueService.toSummary(urgentIgnoredTask))
                .thenReturn(new com.infinitematters.bookkeeping.workflows.ReviewTaskSummary(
                        urgentIgnoredTask.getId(),
                        null,
                        null,
                        "DEAD_LETTER_SUPPORT_PERFORMANCE",
                        "CRITICAL",
                        false,
                        "Ignored performance risk",
                        "Already escalated and still unacknowledged",
                        LocalDate.now().plusDays(1),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0.0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
        when(auditService.countRecentForOrganizationByEventType(
                org.mockito.ArgumentMatchers.eq(organizationId),
                org.mockito.ArgumentMatchers.eq("DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(2L);
        UUID reactivatedTaskId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(auditService.listRecentForOrganizationByEventType(
                organizationId,
                "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED",
                5))
                .thenReturn(List.of(
                        new AuditEventSummary(
                                UUID.randomUUID(),
                                organizationId,
                                userId,
                                "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED",
                                "workflow_task",
                                reactivatedTaskId.toString(),
                                "Reactivated support performance risk after snooze expired on 2026-03-31",
                                Instant.parse("2026-04-01T12:00:00Z"))));

        DashboardSnapshot snapshot = dashboardService.snapshot(organizationId, userId);

        assertThat(snapshot.focusMonth()).isEqualTo(YearMonth.of(2026, 3));
        assertThat(snapshot.cashBalance()).isEqualByComparingTo("120.00");
        assertThat(snapshot.expenseCategories()).isNotEmpty();
        assertThat(snapshot.expenseCategories().get(0).itemId()).isEqualTo("expense-category-software");
        assertThat(snapshot.expenseCategories().get(0).category()).isEqualTo(Category.SOFTWARE);
        assertThat(snapshot.expenseCategories().get(0).amount()).isEqualByComparingTo("60.00");
        assertThat(snapshot.expenseCategories().get(0).deltaFromPreviousMonth()).isEqualByComparingTo("20.00");
        assertThat(snapshot.expenseCategories().get(0).actionKey()).isEqualTo("REVIEW_EXPENSE_CATEGORY");
        assertThat(snapshot.expenseCategories().get(0).actionPath()).isEqualTo("/transactions?category=SOFTWARE");
        assertThat(snapshot.expenseCategories().get(0).actionUrgency()).isEqualTo(DashboardActionUrgency.NORMAL);
        assertThat(snapshot.expenseCategories().get(0).actionReason()).isEqualTo("Up 20.00 from last month.");
        assertThat(snapshot.staleAccounts()).hasSize(1);
        assertThat(snapshot.staleAccounts().get(0).itemId()).isEqualTo("stale-account-" + staleAccountId);
        assertThat(snapshot.staleAccounts().get(0).accountName()).isEqualTo("Reserve Checking");
        assertThat(snapshot.staleAccounts().get(0).actionKey()).isEqualTo("REVIEW_STALE_ACCOUNT");
        assertThat(snapshot.staleAccounts().get(0).actionPath()).isEqualTo("/reconciliation?accountId=" + staleAccountId);
        assertThat(snapshot.staleAccounts().get(0).actionUrgency()).isEqualTo(DashboardActionUrgency.NORMAL);
        assertThat(snapshot.staleAccounts().get(0).actionReason()).isEqualTo("No activity for 45 days.");
        assertThat(snapshot.period().cardId()).isEqualTo("period-close");
        assertThat(snapshot.period().latestCloseMethod()).isEqualTo(PeriodCloseMethod.OVERRIDE);
        assertThat(snapshot.period().unreconciledAccountCount()).isEqualTo(1);
        assertThat(snapshot.period().recommendedActionLabel()).isEqualTo("Finish account reconciliations");
        assertThat(snapshot.period().recommendedActionKey()).isEqualTo("FINISH_RECONCILIATIONS");
        assertThat(snapshot.period().recommendedActionPath()).isEqualTo("/reconciliation");
        assertThat(snapshot.period().recommendedActionUrgency()).isEqualTo(DashboardActionUrgency.HIGH);
        assertThat(snapshot.primaryAction()).isNotNull();
        assertThat(snapshot.primaryAction().cardId()).isEqualTo("support-performance");
        assertThat(snapshot.primaryAction().label()).isEqualTo("Review urgent support risks");
        assertThat(snapshot.primaryAction().actionKey()).isEqualTo("REVIEW_URGENT_SUPPORT_RISKS");
        assertThat(snapshot.primaryAction().actionPath())
                .isEqualTo("/workflows/notifications/dead-letter/performance/tasks/high-priority");
        assertThat(snapshot.primaryAction().itemCount()).isEqualTo(2);
        assertThat(snapshot.primaryAction().reason()).isEqualTo("2 urgent support risks require owner attention.");
        assertThat(snapshot.primaryAction().urgency()).isEqualTo(DashboardActionUrgency.CRITICAL);
        assertThat(snapshot.primaryAction().source()).isEqualTo("SUPPORT_PERFORMANCE");
        assertThat(snapshot.workflowInbox().cardId()).isEqualTo("workflow-inbox");
        assertThat(snapshot.workflowInbox().recommendedActionLabel()).isEqualTo("Review high-priority bookkeeping tasks");
        assertThat(snapshot.workflowInbox().recommendedActionKey()).isEqualTo("REVIEW_HIGH_PRIORITY_TASKS");
        assertThat(snapshot.workflowInbox().recommendedActionPath()).isEqualTo("/workflows/inbox");
        assertThat(snapshot.workflowInbox().recommendedActionUrgency()).isEqualTo(DashboardActionUrgency.HIGH);
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
        assertThat(snapshot.notificationHealth().supportPerformance().cardId()).isEqualTo("support-performance");
        assertThat(snapshot.notificationHealth().supportPerformance().weeks()).isEqualTo(6);
        assertThat(snapshot.notificationHealth().supportPerformance().ignoredEscalationRate()).isEqualTo(0.25);
        assertThat(snapshot.notificationHealth().supportPerformance().openRiskTaskCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportPerformance().acknowledgedRiskTaskCount()).isEqualTo(0);
        assertThat(snapshot.notificationHealth().supportPerformance().snoozedRiskTaskCount()).isEqualTo(0);
        assertThat(snapshot.notificationHealth().supportPerformance().ignoredRiskTaskCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportPerformance().secondaryEscalationCount()).isEqualTo(2);
        assertThat(snapshot.notificationHealth().supportPerformance().recentlyReactivatedCount()).isEqualTo(2);
        assertThat(snapshot.notificationHealth().supportPerformance().recentlyReactivatedNeedsAttentionCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportPerformance().freshlyReactivatedNeedsAttentionCount()).isEqualTo(0);
        assertThat(snapshot.notificationHealth().supportPerformance().reactivatedOverdueCount()).isEqualTo(1);
        assertThat(snapshot.notificationHealth().supportPerformance().averageAssignmentLagHours()).isEqualTo(18.0);
        assertThat(snapshot.notificationHealth().supportPerformance().averageResolutionLagHours()).isEqualTo(54.0);
        assertThat(snapshot.notificationHealth().supportPerformance().ignoredEscalationRateBreached()).isFalse();
        assertThat(snapshot.notificationHealth().supportPerformance().assignmentLagBreached()).isFalse();
        assertThat(snapshot.notificationHealth().supportPerformance().resolutionLagBreached()).isTrue();
        assertThat(snapshot.notificationHealth().supportPerformance().status()).isEqualTo(DeadLetterSupportPerformanceStatus.AT_RISK);
        assertThat(snapshot.notificationHealth().supportPerformance().urgentRiskTaskCount()).isEqualTo(2);
        assertThat(snapshot.notificationHealth().supportPerformance().recommendedActionLabel())
                .isEqualTo("Review urgent support risks");
        assertThat(snapshot.notificationHealth().supportPerformance().recommendedActionKey())
                .isEqualTo("REVIEW_URGENT_SUPPORT_RISKS");
        assertThat(snapshot.notificationHealth().supportPerformance().recommendedActionPath())
                .isEqualTo("/workflows/notifications/dead-letter/performance/tasks/high-priority");
        assertThat(snapshot.notificationHealth().supportPerformance().recommendedActionUrgency())
                .isEqualTo(DashboardActionUrgency.CRITICAL);
        assertThat(snapshot.notificationHealth().supportPerformance().urgentRiskTasks()).hasSize(2);
        assertThat(snapshot.notificationHealth().supportPerformance().urgentRiskTasks().get(0).taskId())
                .isEqualTo(urgentReactivatedTask.getId());
        assertThat(snapshot.notificationHealth().supportPerformance().urgentRiskTasks().get(1).taskId())
                .isEqualTo(urgentIgnoredTask.getId());
        assertThat(snapshot.notificationHealth().supportPerformance().recentReactivations()).hasSize(1);
        assertThat(snapshot.notificationHealth().supportPerformance().recentReactivations().get(0).taskId()).isEqualTo(reactivatedTaskId);
        assertThat(snapshot.notificationHealth().supportPerformance().recentReactivations().get(0).needsAttention()).isTrue();
        assertThat(snapshot.notificationHealth().supportPerformance().recentReactivations().get(0).overdue()).isTrue();
        assertThat(snapshot.notificationHealth().supportPerformance().recentReactivationsNeedingAttention()).hasSize(1);
        assertThat(snapshot.notificationHealth().supportPerformance().recentReactivationsNeedingAttention().get(0).taskId()).isEqualTo(reactivatedTaskId);
        assertThat(snapshot.notificationHealth().topSupportActions()).hasSize(3);
        assertThat(snapshot.notificationHealth().topSupportActions().get(0).action()).isEqualTo(DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY);
        assertThat(snapshot.notificationHealth().recentResolvedDeadLetters()).hasSize(1);
        assertThat(snapshot.notificationHealth().attentionNotifications()).hasSize(1);
    }

    @Test
    void homeSnapshotUsesVersionedFrontendContract() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(organizationService.get(organizationId)).thenReturn(null);
        when(transactionRepository.findFirstByOrganizationIdOrderByTransactionDateDescCreatedAtDesc(organizationId))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumAmountsByOrganizationAndStatusAndAccountTypes(
                organizationId, TransactionStatus.POSTED, List.of(AccountType.BANK, AccountType.CASH)))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.findByOrganizationIdAndStatusOrderByTransactionDateDescCreatedAtDesc(organizationId, TransactionStatus.POSTED))
                .thenReturn(List.of());
        when(categorizationDecisionRepository.findByTransactionOrganizationId(organizationId)).thenReturn(List.of());
        when(reviewQueueService.inbox(organizationId, userId))
                .thenReturn(new WorkflowInboxSummary(
                        "workflow-inbox",
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        List.of()));
        when(closeChecklistService.checklist(organizationId, YearMonth.now()))
                .thenReturn(new CloseChecklistSummary(
                        LocalDate.now().withDayOfMonth(1),
                        LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()),
                        true,
                        List.of()));
        when(accountingPeriodRepository.findFirstByOrganizationIdOrderByPeriodEndDesc(organizationId))
                .thenReturn(Optional.empty());
        when(notificationService.listForOrganization(organizationId)).thenReturn(List.of());
        when(notificationService.operationsSummary(organizationId))
                .thenReturn(new com.infinitematters.bookkeeping.notifications.NotificationOperationsSummary(
                        0, 0, 0, 0, 0, 0, List.of(),
                        new com.infinitematters.bookkeeping.notifications.DeadLetterOperationsSummary(0, 0, 0, List.of())));
        when(notificationService.deadLetterQueue(organizationId))
                .thenReturn(new DeadLetterQueueSummary(List.of(), List.of(), List.of(), List.of()));
        when(deadLetterWorkflowTaskService.operationsSummary(organizationId))
                .thenReturn(new DeadLetterSupportTaskOperationsSummary(0, 0, 0, 0, 0, 0, 0, 0, List.of()));
        when(deadLetterWorkflowTaskService.effectivenessSummary(organizationId, 6))
                .thenReturn(new DeadLetterSupportEffectivenessSummary(
                        LocalDate.now().minusWeeks(6),
                        LocalDate.now(),
                        6,
                        0,
                        0,
                        0,
                        0,
                        List.of()));
        when(deadLetterWorkflowTaskService.performanceSummary(organizationId, 6))
                .thenReturn(new DeadLetterSupportPerformanceSummary(
                        LocalDate.now().minusWeeks(6),
                        LocalDate.now(),
                        6,
                        0,
                        0.0,
                        null,
                        null,
                        false,
                        false,
                        false,
                        DeadLetterSupportPerformanceStatus.ON_TRACK));
        when(deadLetterSupportPerformanceMonitorService.taskStatus(organizationId))
                .thenReturn(new DeadLetterSupportPerformanceTaskStatus(0, 0, 0, 0, 0));
        when(deadLetterSupportPerformanceMonitorService.listHighPriorityRiskTasks(organizationId)).thenReturn(List.of());
        when(deadLetterSupportPerformanceMonitorService.listOpenRiskTasks(
                organizationId, DeadLetterSupportPerformanceTaskFilter.ALL)).thenReturn(List.of());
        when(auditService.countRecentForOrganizationByEventType(
                org.mockito.ArgumentMatchers.eq(organizationId),
                org.mockito.ArgumentMatchers.eq("DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED"),
                org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(auditService.listRecentForOrganizationByEventType(
                organizationId,
                "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED",
                5)).thenReturn(List.of());

        DashboardHomeSnapshot home = dashboardService.homeSnapshot(organizationId, userId, "v1");

        assertThat(home.version()).isEqualTo("v1");
        assertThat(home.primaryAction()).isNull();
        assertThat(home.supportPerformance().cardId()).isEqualTo("support-performance");
        assertThat(home.expenseCategories()).isEmpty();
        assertThat(home.staleAccounts()).isEmpty();
        assertThat(home.recentNotifications()).isEmpty();
    }

    @Test
    void homeSnapshotRejectsUnsupportedVersion() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> dashboardService.homeSnapshot(organizationId, userId, "v2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported dashboard home version 'v2'. Supported versions: v1.");
    }

    @Test
    void homeResponsePackagesMetadataNegotiationAndSnapshot() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(transactionRepository.findFirstByOrganizationIdOrderByTransactionDateDescCreatedAtDesc(organizationId))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumAmountsByOrganizationAndStatusAndAccountTypes(
                organizationId, TransactionStatus.POSTED, List.of(AccountType.BANK, AccountType.CASH)))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.findByOrganizationIdAndStatusOrderByTransactionDateDescCreatedAtDesc(
                organizationId, TransactionStatus.POSTED))
                .thenReturn(List.of());
        when(categorizationDecisionRepository.findByTransactionOrganizationId(organizationId))
                .thenReturn(List.of());
        when(reviewQueueService.inbox(organizationId, userId))
                .thenReturn(new WorkflowInboxSummary(
                        "workflow-inbox",
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        List.of()));
        when(closeChecklistService.checklist(organizationId, YearMonth.now()))
                .thenReturn(new CloseChecklistSummary(
                        YearMonth.now().atDay(1),
                        YearMonth.now().atEndOfMonth(),
                        true,
                        List.of()));
        when(accountingPeriodRepository.findFirstByOrganizationIdOrderByPeriodEndDesc(organizationId))
                .thenReturn(Optional.empty());
        when(notificationService.listForOrganization(organizationId)).thenReturn(List.of());
        when(notificationService.operationsSummary(organizationId))
                .thenReturn(new com.infinitematters.bookkeeping.notifications.NotificationOperationsSummary(
                        0, 0, 0, 0, 0, 0, List.of(),
                        new com.infinitematters.bookkeeping.notifications.DeadLetterOperationsSummary(0, 0, 0, List.of())));
        when(notificationService.deadLetterQueue(organizationId))
                .thenReturn(new DeadLetterQueueSummary(List.of(), List.of(), List.of(), List.of()));
        when(deadLetterWorkflowTaskService.operationsSummary(organizationId))
                .thenReturn(new DeadLetterSupportTaskOperationsSummary(0, 0, 0, 0, 0, 0, 0, 0, List.of()));
        when(deadLetterWorkflowTaskService.effectivenessSummary(organizationId, 6))
                .thenReturn(new DeadLetterSupportEffectivenessSummary(
                        LocalDate.now().minusWeeks(5),
                        LocalDate.now(),
                        6,
                        0,
                        0,
                        0,
                        0,
                        List.of()));
        when(deadLetterWorkflowTaskService.performanceSummary(organizationId, 6))
                .thenReturn(new DeadLetterSupportPerformanceSummary(
                        LocalDate.now().minusWeeks(5),
                        LocalDate.now(),
                        6,
                        0,
                        0.0,
                        null,
                        null,
                        false,
                        false,
                        false,
                        DeadLetterSupportPerformanceStatus.ON_TRACK));
        when(deadLetterSupportPerformanceMonitorService.taskStatus(organizationId))
                .thenReturn(new DeadLetterSupportPerformanceTaskStatus(0, 0, 0, 0, 0));
        when(deadLetterSupportPerformanceMonitorService.listHighPriorityRiskTasks(organizationId))
                .thenReturn(List.of());
        when(deadLetterSupportPerformanceMonitorService.listOpenRiskTasks(
                organizationId, DeadLetterSupportPerformanceTaskFilter.ALL))
                .thenReturn(List.of());
        when(auditService.countRecentForOrganizationByEventType(
                eq(organizationId), eq("DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED"), any(Instant.class)))
                .thenReturn(0L);
        when(auditService.listRecentForOrganizationByEventType(
                organizationId, "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED", 5))
                .thenReturn(List.of());

        DashboardHomeResponse response = dashboardService.homeResponse(organizationId, userId, "v1", false);

        assertThat(response.metadata().defaultVersion()).isEqualTo("v1");
        assertThat(response.negotiation().version().value()).isEqualTo("v1");
        assertThat(response.negotiation().requestedVersion()).isEqualTo("v1");
        assertThat(response.negotiation().versionSource()).isEqualTo("default");
        assertThat(response.snapshot().contract()).isEqualTo(response.negotiation().snapshot());
        assertThat(response.snapshot().version()).isEqualTo(response.negotiation().version().value());
    }

    @Test
    void homeContractMetadataExposesSupportedVersions() {
        DashboardHomeContractMetadata metadata = dashboardService.homeContractMetadata();
        DashboardHomeContractMetadata expected = DashboardHomeContractTestFixtures.metadataV1();

        assertThat(metadata).isEqualTo(expected);
    }

    @Test
    void homeVersionsResponsePackagesMetadata() {
        DashboardHomeVersionsResponse response = dashboardService.homeVersionsResponse();
        DashboardHomeContractMetadata expected = DashboardHomeContractTestFixtures.metadataV1();

        assertThat(response.metadata()).isEqualTo(expected);
    }

    @Test
    void homeSnapshotIncludesContractNegotiationDetails() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(transactionRepository.findFirstByOrganizationIdOrderByTransactionDateDescCreatedAtDesc(organizationId))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumAmountsByOrganizationAndStatusAndAccountTypes(
                organizationId, TransactionStatus.POSTED, List.of(AccountType.BANK, AccountType.CASH)))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.findByOrganizationIdAndStatusOrderByTransactionDateDescCreatedAtDesc(organizationId, TransactionStatus.POSTED))
                .thenReturn(List.of());
        when(categorizationDecisionRepository.findByTransactionOrganizationId(organizationId))
                .thenReturn(List.of());
        when(reviewQueueService.inbox(organizationId, userId))
                .thenReturn(new WorkflowInboxSummary(
                        "workflow-inbox",
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        List.of()));
        when(closeChecklistService.checklist(organizationId, YearMonth.now()))
                .thenReturn(new CloseChecklistSummary(
                        YearMonth.now().atDay(1),
                        YearMonth.now().atEndOfMonth(),
                        true,
                        List.of()));
        when(accountingPeriodRepository.findFirstByOrganizationIdOrderByPeriodEndDesc(organizationId))
                .thenReturn(Optional.empty());
        when(notificationService.listForOrganization(organizationId)).thenReturn(List.of());
        when(notificationService.operationsSummary(organizationId))
                .thenReturn(new com.infinitematters.bookkeeping.notifications.NotificationOperationsSummary(
                        0, 0, 0, 0, 0, 0, List.of(),
                        new com.infinitematters.bookkeeping.notifications.DeadLetterOperationsSummary(0, 0, 0, List.of())));
        when(notificationService.deadLetterQueue(organizationId))
                .thenReturn(new DeadLetterQueueSummary(List.of(), List.of(), List.of(), List.of()));
        when(deadLetterWorkflowTaskService.operationsSummary(organizationId))
                .thenReturn(new DeadLetterSupportTaskOperationsSummary(0, 0, 0, 0, 0, 0, 0, 0, List.of()));
        when(deadLetterWorkflowTaskService.effectivenessSummary(organizationId, 6))
                .thenReturn(new DeadLetterSupportEffectivenessSummary(
                        LocalDate.now().minusWeeks(5),
                        LocalDate.now(),
                        6,
                        0,
                        0,
                        0,
                        0,
                        List.of()));
        when(deadLetterWorkflowTaskService.performanceSummary(organizationId, 6))
                .thenReturn(new DeadLetterSupportPerformanceSummary(
                        LocalDate.now().minusWeeks(5),
                        LocalDate.now(),
                        6,
                        0,
                        0.0,
                        null,
                        null,
                        false,
                        false,
                        false,
                        DeadLetterSupportPerformanceStatus.ON_TRACK));
        when(deadLetterSupportPerformanceMonitorService.taskStatus(organizationId))
                .thenReturn(new DeadLetterSupportPerformanceTaskStatus(0, 0, 0, 0, 0));
        when(deadLetterSupportPerformanceMonitorService.listHighPriorityRiskTasks(organizationId))
                .thenReturn(List.of());
        when(deadLetterSupportPerformanceMonitorService.listOpenRiskTasks(
                organizationId, DeadLetterSupportPerformanceTaskFilter.ALL))
                .thenReturn(List.of());
        when(auditService.countRecentForOrganizationByEventType(
                eq(organizationId), eq("DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED"), any(Instant.class)))
                .thenReturn(0L);
        when(auditService.listRecentForOrganizationByEventType(
                organizationId, "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED", 5))
                .thenReturn(List.of());

        DashboardHomeSnapshot implicit = dashboardService.homeSnapshot(
                organizationId,
                userId,
                DashboardHomeContractVersion.defaultVersion().value(),
                false);
        DashboardHomeSnapshot explicit = dashboardService.homeSnapshot(organizationId, userId, "v1");

        assertThat(implicit.contract().version()).isEqualTo("v1");
        assertThat(implicit.contract().requestedVersion()).isEqualTo("v1");
        assertThat(implicit.contract().versionSource()).isEqualTo("default");
        assertThat(explicit.contract().version()).isEqualTo("v1");
        assertThat(explicit.contract().requestedVersion()).isEqualTo("v1");
        assertThat(explicit.contract().versionSource()).isEqualTo("requested");
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

    private com.infinitematters.bookkeeping.workflows.WorkflowTask workflowTask(UUID id) {
        com.infinitematters.bookkeeping.workflows.WorkflowTask task = new com.infinitematters.bookkeeping.workflows.WorkflowTask();
        setField(task, "id", id);
        task.setDueDate(LocalDate.now().minusDays(1));
        return task;
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
