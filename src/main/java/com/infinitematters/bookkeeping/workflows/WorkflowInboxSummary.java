package com.infinitematters.bookkeeping.workflows;

import com.infinitematters.bookkeeping.dashboard.DashboardActionUrgency;

import java.util.List;

public record WorkflowInboxSummary(
        String cardId,
        int openCount,
        int overdueCount,
        int dueTodayCount,
        int highPriorityCount,
        int unassignedCount,
        int assignedToCurrentUserCount,
        String recommendedActionLabel,
        String recommendedActionKey,
        String recommendedActionPath,
        DashboardActionUrgency recommendedActionUrgency,
        List<ReviewTaskSummary> attentionTasks) {
}
