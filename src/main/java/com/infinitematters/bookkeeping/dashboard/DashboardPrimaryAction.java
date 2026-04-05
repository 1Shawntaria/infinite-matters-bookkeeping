package com.infinitematters.bookkeeping.dashboard;

public record DashboardPrimaryAction(
        String cardId,
        String label,
        String actionKey,
        String actionPath,
        Long itemCount,
        String reason,
        DashboardActionUrgency urgency,
        String source) {
}
