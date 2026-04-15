package com.infinitematters.bookkeeping.dashboard;

import org.springframework.http.HttpHeaders;

import java.util.List;

public final class DashboardHomeContractTestFixtures {
    private DashboardHomeContractTestFixtures() {
    }

    public static DashboardHomeContractMetadata metadataV1() {
        return new DashboardHomeContractMetadata(
                "v1",
                "v1",
                "v1",
                "If the client omits a version, the server returns the default version. "
                        + "If the client requests an unsupported version, the server returns 400 Bad Request.",
                "X-Dashboard-Home-Default-Version is the server default, "
                        + "X-Dashboard-Home-Recommended-Version is the preferred client target, "
                        + "X-Dashboard-Home-Latest-Version is the newest available contract, "
                        + "and X-Dashboard-Home-Supported-Versions lists all supported versions.",
                List.of("v1"),
                List.of(new DashboardHomeContractVersionMetadata(
                        "v1",
                        true,
                        "Current stable home dashboard contract.",
                        "Recommended for all current clients.",
                        false,
                        null,
                        null)));
    }

    public static HttpHeaders versionDiscoveryHeadersV1() {
        return metadataV1().headerValues().toHttpHeaders();
    }

    public static HttpHeaders homeHeadersV1Default() {
        return metadataV1()
                .headerValues(DashboardHomeContractNegotiation.negotiate("v1", false).responseHeaderValues())
                .toHttpHeaders();
    }

    public static HttpHeaders homeHeadersV1Requested() {
        return metadataV1()
                .headerValues(DashboardHomeContractNegotiation.negotiate("v1", true).responseHeaderValues())
                .toHttpHeaders();
    }
}
