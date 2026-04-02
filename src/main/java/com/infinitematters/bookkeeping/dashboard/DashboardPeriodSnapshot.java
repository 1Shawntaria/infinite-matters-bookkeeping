package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.periods.AccountingPeriodStatus;
import com.infinitematters.bookkeeping.periods.PeriodCloseMethod;

public record DashboardPeriodSnapshot(
        boolean closeReady,
        int unreconciledAccountCount,
        AccountingPeriodStatus latestClosedStatus,
        PeriodCloseMethod latestCloseMethod,
        String latestOverrideReason) {
}
