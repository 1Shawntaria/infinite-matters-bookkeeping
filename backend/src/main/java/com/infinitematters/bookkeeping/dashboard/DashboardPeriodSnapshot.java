package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.periods.AccountingPeriodStatus;
import com.infinitematters.bookkeeping.periods.PeriodCloseMethod;

public record DashboardPeriodSnapshot(
        String cardId,
        boolean closeReady,
        int unreconciledAccountCount,
        String recommendedActionLabel,
        String recommendedActionKey,
        String recommendedActionPath,
        DashboardActionUrgency recommendedActionUrgency,
        AccountingPeriodStatus latestClosedStatus,
        PeriodCloseMethod latestCloseMethod,
        String latestOverrideReason) {
}
