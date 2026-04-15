package com.infinitematters.bookkeeping.dashboard;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public enum DashboardHomeContractVersion {
    V1(
            "v1",
            false,
            null,
            null,
            "Current stable home dashboard contract.",
            "Recommended for all current clients.");

    private final String value;
    private final boolean deprecated;
    private final LocalDate deprecationDate;
    private final LocalDate sunsetDate;
    private final String notes;
    private final String intendedUse;
    private static final DateTimeFormatter SUNSET_HEADER_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;

    DashboardHomeContractVersion(String value,
                                 boolean deprecated,
                                 LocalDate deprecationDate,
                                 LocalDate sunsetDate,
                                 String notes,
                                 String intendedUse) {
        this.value = value;
        this.deprecated = deprecated;
        this.deprecationDate = deprecationDate;
        this.sunsetDate = sunsetDate;
        this.notes = notes;
        this.intendedUse = intendedUse;
    }

    public String value() {
        return value;
    }

    public static DashboardHomeContractVersion defaultVersion() {
        return V1;
    }

    public static DashboardHomeContractVersion parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultVersion();
        }
        for (DashboardHomeContractVersion version : values()) {
            if (version.value.equalsIgnoreCase(rawValue.trim())) {
                return version;
            }
        }
        throw new IllegalArgumentException(
                "Unsupported dashboard home version '%s'. Supported versions: %s."
                        .formatted(rawValue, supportedVersions()));
    }

    public static String supportedVersions() {
        return Arrays.stream(values())
                .map(DashboardHomeContractVersion::value)
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    public boolean deprecated() {
        return deprecated;
    }

    public LocalDate deprecationDate() {
        return deprecationDate;
    }

    public LocalDate sunsetDate() {
        return sunsetDate;
    }

    public String notes() {
        return notes;
    }

    public String intendedUse() {
        return intendedUse;
    }

    public static List<String> supportedVersionValues() {
        return Arrays.stream(values())
                .map(DashboardHomeContractVersion::value)
                .sorted()
                .toList();
    }

    public DashboardHomeContractResponseHeaderValues responseHeaderValues(String requestedVersion,
                                                                         boolean explicitVersionRequested) {
        return new DashboardHomeContractResponseHeaderValues(
                value,
                requestedVersion,
                explicitVersionRequested ? "requested" : "default",
                Boolean.toString(deprecated),
                deprecated ? "true" : null,
                sunsetDate != null
                        ? sunsetDate.atStartOfDay(ZoneOffset.UTC).format(SUNSET_HEADER_FORMATTER)
                        : null);
    }
}
