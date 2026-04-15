package com.infinitematters.bookkeeping.dashboard;

public record DashboardHomeContractNegotiation(
        DashboardHomeContractVersion version,
        String requestedVersion,
        String versionSource) {

    public static DashboardHomeContractNegotiation negotiate(String requestedVersion,
                                                             boolean explicitVersionRequested) {
        DashboardHomeContractVersion negotiatedVersion = DashboardHomeContractVersion.parse(requestedVersion);
        return new DashboardHomeContractNegotiation(
                negotiatedVersion,
                requestedVersion,
                explicitVersionRequested ? "requested" : "default");
    }

    public DashboardHomeContractSnapshot snapshot() {
        return new DashboardHomeContractSnapshot(
                version.value(),
                requestedVersion,
                versionSource);
    }

    public DashboardHomeContractResponseHeaderValues responseHeaderValues() {
        return version.responseHeaderValues(requestedVersion, "requested".equals(versionSource));
    }
}
