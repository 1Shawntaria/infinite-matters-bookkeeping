package com.infinitematters.bookkeeping.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Metadata for one supported dashboard home contract version.")
public record DashboardHomeContractVersionMetadata(
        @Schema(description = "Contract version identifier", example = "v1")
        String version,
        @Schema(description = "Whether this version is the current default for clients")
        boolean defaultVersion,
        @Schema(description = "Short backend-owned notes about this contract version", example = "Current stable home dashboard contract.")
        String notes,
        @Schema(description = "Guidance for when clients should use this contract version", example = "Recommended for all current clients.")
        String intendedUse,
        @Schema(description = "Whether this version is deprecated")
        boolean deprecated,
        @Schema(description = "Date on which this version was marked deprecated, if applicable", example = "2026-10-01", nullable = true)
        LocalDate deprecationDate,
        @Schema(description = "Planned sunset date for deprecated versions, if known", example = "2026-12-31", nullable = true)
        LocalDate sunsetDate) {
}
