package com.infinitematters.bookkeeping.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Discovery metadata for supported dashboard home contract versions.")
public record DashboardHomeContractMetadata(
        @Schema(description = "Current default dashboard home contract version", example = "v1")
        String defaultVersion,
        @Schema(description = "Recommended dashboard home contract version for new or migrating clients", example = "v1")
        String recommendedVersion,
        @Schema(description = "Latest available dashboard home contract version exposed by the API", example = "v1")
        String latestVersion,
        @Schema(
                description = "How the server negotiates dashboard home versions when the client omits a version or requests an unsupported one",
                example = "If the client omits a version, the server returns the default version. If the client requests an unsupported version, the server returns 400 Bad Request.")
        String negotiationPolicy,
        @Schema(
                description = "What the version lifecycle headers on discovery and home responses mean",
                example = "X-Dashboard-Home-Default-Version is the server default, X-Dashboard-Home-Recommended-Version is the preferred client target, X-Dashboard-Home-Latest-Version is the newest available contract, and X-Dashboard-Home-Supported-Versions lists all supported versions.")
        String headerPolicy,
        @Schema(description = "Supported dashboard home contract versions in preference order")
        List<String> supportedVersions,
        @Schema(description = "Detailed metadata for each supported contract version")
        List<DashboardHomeContractVersionMetadata> versions) {

    public String supportedVersionsHeaderValue() {
        return String.join(", ", supportedVersions);
    }

    public DashboardHomeContractHeaderValues headerValues() {
        return new DashboardHomeContractHeaderValues(
                defaultVersion,
                recommendedVersion,
                latestVersion,
                supportedVersionsHeaderValue(),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public DashboardHomeContractHeaderValues headerValues(DashboardHomeContractResponseHeaderValues responseHeaderValues) {
        return new DashboardHomeContractHeaderValues(
                defaultVersion,
                recommendedVersion,
                latestVersion,
                supportedVersionsHeaderValue(),
                responseHeaderValues.version(),
                responseHeaderValues.requestedVersion(),
                responseHeaderValues.versionSource(),
                responseHeaderValues.deprecated(),
                responseHeaderValues.deprecation(),
                responseHeaderValues.sunset());
    }
}
