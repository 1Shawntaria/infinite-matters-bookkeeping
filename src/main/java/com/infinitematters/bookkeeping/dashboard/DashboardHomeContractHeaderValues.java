package com.infinitematters.bookkeeping.dashboard;

import org.springframework.http.HttpHeaders;

public record DashboardHomeContractHeaderValues(
        String defaultVersion,
        String recommendedVersion,
        String latestVersion,
        String supportedVersions,
        String version,
        String requestedVersion,
        String versionSource,
        String deprecated,
        String deprecation,
        String sunset) {
    private static final String HOME_VERSION_HEADER = "X-Dashboard-Home-Version";
    private static final String HOME_REQUESTED_VERSION_HEADER = "X-Dashboard-Home-Requested-Version";
    private static final String HOME_VERSION_SOURCE_HEADER = "X-Dashboard-Home-Version-Source";
    private static final String HOME_DEFAULT_VERSION_HEADER = "X-Dashboard-Home-Default-Version";
    private static final String HOME_RECOMMENDED_VERSION_HEADER = "X-Dashboard-Home-Recommended-Version";
    private static final String HOME_LATEST_VERSION_HEADER = "X-Dashboard-Home-Latest-Version";
    private static final String HOME_SUPPORTED_VERSIONS_HEADER = "X-Dashboard-Home-Supported-Versions";
    private static final String HOME_DEPRECATED_HEADER = "X-Dashboard-Home-Deprecated";
    private static final String DEPRECATION_HEADER = "Deprecation";
    private static final String SUNSET_HEADER = "Sunset";

    public HttpHeaders toHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HOME_DEFAULT_VERSION_HEADER, defaultVersion);
        headers.add(HOME_RECOMMENDED_VERSION_HEADER, recommendedVersion);
        headers.add(HOME_LATEST_VERSION_HEADER, latestVersion);
        headers.add(HOME_SUPPORTED_VERSIONS_HEADER, supportedVersions);
        if (version != null) {
            headers.add(HOME_VERSION_HEADER, version);
        }
        if (requestedVersion != null) {
            headers.add(HOME_REQUESTED_VERSION_HEADER, requestedVersion);
        }
        if (versionSource != null) {
            headers.add(HOME_VERSION_SOURCE_HEADER, versionSource);
        }
        if (deprecated != null) {
            headers.add(HOME_DEPRECATED_HEADER, deprecated);
        }
        if (deprecation != null) {
            headers.add(DEPRECATION_HEADER, deprecation);
        }
        if (sunset != null) {
            headers.add(SUNSET_HEADER, sunset);
        }
        return headers;
    }
}
