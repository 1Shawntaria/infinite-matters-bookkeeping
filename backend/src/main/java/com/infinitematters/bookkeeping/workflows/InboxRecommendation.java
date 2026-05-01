package com.infinitematters.bookkeeping.workflows;

import com.infinitematters.bookkeeping.dashboard.DashboardActionUrgency;

public record InboxRecommendation(
        String label,
        String key,
        String path,
        DashboardActionUrgency urgency) {
}
