package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.domain.Category;

import java.math.BigDecimal;

public record DashboardExpenseCategorySummary(
        Category category,
        BigDecimal amount,
        BigDecimal deltaFromPreviousMonth) {
}
