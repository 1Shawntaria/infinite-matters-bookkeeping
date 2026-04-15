package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.domain.Category;

import java.math.BigDecimal;

public record DashboardExpenseCategorySummary(
        String itemId,
        Category category,
        BigDecimal amount,
        BigDecimal deltaFromPreviousMonth,
        String actionKey,
        String actionPath,
        DashboardActionUrgency actionUrgency,
        String actionReason) {
}
