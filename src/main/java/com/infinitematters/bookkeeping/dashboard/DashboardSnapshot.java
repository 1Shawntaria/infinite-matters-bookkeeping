package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.notifications.NotificationSummary;
import com.infinitematters.bookkeeping.workflows.WorkflowInboxSummary;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record DashboardSnapshot(
        YearMonth focusMonth,
        BigDecimal cashBalance,
        long postedTransactionCount,
        DashboardPrimaryAction primaryAction,
        WorkflowInboxSummary workflowInbox,
        DashboardPeriodSnapshot period,
        DashboardNotificationHealthSnapshot notificationHealth,
        List<DashboardExpenseCategorySummary> expenseCategories,
        List<DashboardStaleAccountSummary> staleAccounts,
        List<NotificationSummary> recentNotifications) {
}
