package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.notifications.NotificationSummary;
import com.infinitematters.bookkeeping.workflows.WorkflowInboxSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Schema(
        name = "DashboardHomeSnapshot",
        description = "Versioned frontend-facing home dashboard contract. "
                + "This payload is intentionally narrower and more stable than the broader dashboard snapshot. "
                + "Clients should negotiate this shape through the /api/dashboard/home version parameter.")
public record DashboardHomeSnapshot(
        @Schema(description = "Contract version for the home dashboard payload", example = "v1")
        String version,
        @Schema(description = "Negotiation details for the versioned home contract")
        DashboardHomeContractSnapshot contract,
        @Schema(description = "Current bookkeeping focus month", example = "2026-03")
        YearMonth focusMonth,
        @Schema(description = "Current cash balance across included cash and bank accounts", example = "128.43")
        BigDecimal cashBalance,
        @Schema(description = "Count of posted transactions represented in the current focus state", example = "3")
        long postedTransactionCount,
        @Schema(description = "Highest-priority home action for the current user and organization")
        DashboardPrimaryAction primaryAction,
        @Schema(description = "Workflow inbox card for pending bookkeeping work")
        WorkflowInboxSummary workflowInbox,
        @Schema(description = "Period close and reconciliation card")
        DashboardPeriodSnapshot period,
        @Schema(description = "Support-performance card for urgent operational risk")
        DashboardDeadLetterSupportPerformanceSnapshot supportPerformance,
        @Schema(description = "Top expense-category rows for the home view")
        List<DashboardExpenseCategorySummary> expenseCategories,
        @Schema(description = "Stale-account rows for the home view")
        List<DashboardStaleAccountSummary> staleAccounts,
        @Schema(description = "Most recent notifications relevant to the home view")
        List<NotificationSummary> recentNotifications) {
}
