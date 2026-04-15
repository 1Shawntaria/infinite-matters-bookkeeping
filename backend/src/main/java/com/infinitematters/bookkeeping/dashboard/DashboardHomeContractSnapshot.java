package com.infinitematters.bookkeeping.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Negotiation details for the versioned dashboard home contract.")
public record DashboardHomeContractSnapshot(
        @Schema(description = "Negotiated dashboard home contract version returned by the API", example = "v1")
        String version,
        @Schema(description = "Original requested dashboard home contract version, or the default when omitted", example = "v1")
        String requestedVersion,
        @Schema(description = "How the response version was selected", allowableValues = {"requested", "default"}, example = "requested")
        String versionSource) {
}
