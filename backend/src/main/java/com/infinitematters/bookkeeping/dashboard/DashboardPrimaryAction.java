package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.workflows.CloseFollowUpSeverity;

public record DashboardPrimaryAction(
        String cardId,
        String label,
        String actionKey,
        String actionPath,
        Long itemCount,
        String reason,
        DashboardActionUrgency urgency,
        CloseFollowUpSeverity severity,
        String source) {
}
