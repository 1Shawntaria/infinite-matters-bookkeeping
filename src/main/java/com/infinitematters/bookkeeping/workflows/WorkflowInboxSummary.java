package com.infinitematters.bookkeeping.workflows;

import java.util.List;

public record WorkflowInboxSummary(
        int openCount,
        int overdueCount,
        int dueTodayCount,
        int highPriorityCount,
        int unassignedCount,
        int assignedToCurrentUserCount,
        List<ReviewTaskSummary> attentionTasks) {
}
