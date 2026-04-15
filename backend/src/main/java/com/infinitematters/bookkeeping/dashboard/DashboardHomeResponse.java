package com.infinitematters.bookkeeping.dashboard;

public record DashboardHomeResponse(
        DashboardHomeContractMetadata metadata,
        DashboardHomeContractNegotiation negotiation,
        DashboardHomeSnapshot snapshot) {
}
