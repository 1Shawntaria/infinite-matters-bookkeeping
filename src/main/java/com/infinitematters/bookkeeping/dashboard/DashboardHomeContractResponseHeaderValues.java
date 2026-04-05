package com.infinitematters.bookkeeping.dashboard;

public record DashboardHomeContractResponseHeaderValues(
        String version,
        String requestedVersion,
        String versionSource,
        String deprecated,
        String deprecation,
        String sunset) {
}
