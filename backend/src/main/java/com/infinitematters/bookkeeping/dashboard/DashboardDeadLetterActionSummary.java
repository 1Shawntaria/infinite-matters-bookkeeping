package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.notifications.DeadLetterRecommendedAction;

public record DashboardDeadLetterActionSummary(
        DeadLetterRecommendedAction action,
        long count,
        String label) {
}
