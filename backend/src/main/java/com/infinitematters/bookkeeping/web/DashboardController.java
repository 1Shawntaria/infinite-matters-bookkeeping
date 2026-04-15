package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.dashboard.DashboardService;
import com.infinitematters.bookkeeping.dashboard.DashboardHomeContractMetadata;
import com.infinitematters.bookkeeping.dashboard.DashboardHomeResponse;
import com.infinitematters.bookkeeping.dashboard.DashboardHomeContractVersion;
import com.infinitematters.bookkeeping.dashboard.DashboardHomeSnapshot;
import com.infinitematters.bookkeeping.dashboard.DashboardHomeVersionsResponse;
import com.infinitematters.bookkeeping.dashboard.DashboardSnapshot;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Tenant-safe dashboard and home-screen API")
public class DashboardController {
    private static final String HOME_DEFAULT_VERSION_HEADER = "X-Dashboard-Home-Default-Version";
    private static final String HOME_RECOMMENDED_VERSION_HEADER = "X-Dashboard-Home-Recommended-Version";
    private static final String HOME_LATEST_VERSION_HEADER = "X-Dashboard-Home-Latest-Version";
    private static final String HOME_SUPPORTED_VERSIONS_HEADER = "X-Dashboard-Home-Supported-Versions";
    private static final String HOME_VERSION_HEADER = "X-Dashboard-Home-Version";
    private static final String HOME_REQUESTED_VERSION_HEADER = "X-Dashboard-Home-Requested-Version";
    private static final String HOME_VERSION_SOURCE_HEADER = "X-Dashboard-Home-Version-Source";
    private static final String HOME_DEPRECATED_HEADER = "X-Dashboard-Home-Deprecated";
    private static final String DEPRECATION_HEADER = "Deprecation";
    private static final String SUNSET_HEADER = "Sunset";

    private final DashboardService dashboardService;
    private final TenantAccessService tenantAccessService;

    public DashboardController(DashboardService dashboardService,
                               TenantAccessService tenantAccessService) {
        this.dashboardService = dashboardService;
        this.tenantAccessService = tenantAccessService;
    }

    @GetMapping("/snapshot")
    @Operation(
            summary = "Get the full dashboard snapshot",
            description = "Returns the broader dashboard payload used for operational and bookkeeping views. "
                    + "Frontend home-screen clients should prefer /api/dashboard/home for the versioned contract boundary.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Dashboard snapshot returned"),
                    @ApiResponse(responseCode = "403", description = "Caller does not have access to the organization",
                            content = @Content(schema = @Schema(hidden = true)))
            })
    public DashboardSnapshot snapshot(
            @Parameter(description = "Organization id for the tenant dashboard view")
            @RequestParam UUID organizationId) {
        UUID userId = tenantAccessService.requireAccess(organizationId);
        return dashboardService.snapshot(organizationId, userId);
    }

    @GetMapping("/home")
    @Operation(
            summary = "Get the versioned home dashboard contract",
            description = "Returns the versioned home-screen payload for frontend clients. "
                    + "This endpoint is the dashboard contract of record for the app shell and should remain stable across broader dashboard evolution. "
                    + "Clients may request an explicit contract version; unsupported versions return 400.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Versioned home snapshot returned",
                            headers = {
                                    @Header(name = HOME_VERSION_HEADER, description = "Negotiated dashboard home contract version", schema = @Schema(example = "v1")),
                                    @Header(name = HOME_REQUESTED_VERSION_HEADER, description = "Original requested dashboard home contract version, or the default version when the client omitted it", schema = @Schema(example = "v1")),
                                    @Header(name = HOME_VERSION_SOURCE_HEADER, description = "Whether the response version came from an explicit client request or the server default", schema = @Schema(example = "requested")),
                                    @Header(name = HOME_DEFAULT_VERSION_HEADER, description = "Current default dashboard home contract version", schema = @Schema(example = "v1")),
                                    @Header(name = HOME_RECOMMENDED_VERSION_HEADER, description = "Recommended dashboard home contract version for new or migrating clients", schema = @Schema(example = "v1")),
                                    @Header(name = HOME_LATEST_VERSION_HEADER, description = "Latest available dashboard home contract version exposed by the API", schema = @Schema(example = "v1")),
                                    @Header(name = HOME_SUPPORTED_VERSIONS_HEADER, description = "Comma-separated supported dashboard home contract versions", schema = @Schema(example = "v1")),
                                    @Header(name = HOME_DEPRECATED_HEADER, description = "Whether the negotiated contract version is deprecated", schema = @Schema(example = "false")),
                                    @Header(name = DEPRECATION_HEADER, description = "Present when the negotiated contract version is deprecated", schema = @Schema(example = "true")),
                                    @Header(name = SUNSET_HEADER, description = "Lifecycle sunset date for the negotiated version when deprecated", schema = @Schema(example = "Wed, 31 Dec 2026 00:00:00 GMT"))
                            }),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Unsupported home contract version requested. Negotiated dashboard-home version headers are not emitted on this error response.",
                            content = @Content(schema = @Schema(hidden = true))),
                    @ApiResponse(responseCode = "403", description = "Caller does not have access to the organization",
                            content = @Content(schema = @Schema(hidden = true)))
            })
    public ResponseEntity<DashboardHomeSnapshot> home(
            @Parameter(description = "Organization id for the versioned home-screen contract")
            @RequestParam UUID organizationId,
            @Parameter(
                    description = "Requested home contract version",
                    schema = @Schema(allowableValues = {"v1"}, defaultValue = "v1"))
            @RequestParam(required = false) String version) {
        UUID userId = tenantAccessService.requireAccess(organizationId);
        String requestedVersion = version != null ? version : DashboardHomeContractVersion.defaultVersion().value();
        DashboardHomeResponse response = dashboardService.homeResponse(
                organizationId,
                userId,
                requestedVersion,
                version != null);
        return ResponseEntity.ok()
                .headers(response.metadata().headerValues(response.negotiation().responseHeaderValues()).toHttpHeaders())
                .body(response.snapshot());
    }

    @GetMapping("/home/versions")
    @Operation(
            summary = "Get supported dashboard home contract versions",
            description = "Returns discovery metadata for the versioned home dashboard contract so clients can choose a supported version before calling /api/dashboard/home. "
                    + "The response also explains the negotiation policy for omitted and unsupported versions and documents the version lifecycle headers "
                    + "returned with discovery responses, including the default, recommended, latest, and supported contract versions.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Supported home contract versions returned",
                            headers = {
                                    @Header(name = HOME_DEFAULT_VERSION_HEADER, description = "Current default dashboard home contract version", schema = @Schema(example = "v1")),
                                    @Header(name = HOME_RECOMMENDED_VERSION_HEADER, description = "Recommended dashboard home contract version for new or migrating clients", schema = @Schema(example = "v1")),
                                    @Header(name = HOME_LATEST_VERSION_HEADER, description = "Latest available dashboard home contract version exposed by the API", schema = @Schema(example = "v1")),
                                    @Header(name = HOME_SUPPORTED_VERSIONS_HEADER, description = "Comma-separated supported dashboard home contract versions", schema = @Schema(example = "v1"))
                            })
            })
    public ResponseEntity<DashboardHomeContractMetadata> homeVersions() {
        DashboardHomeVersionsResponse response = dashboardService.homeVersionsResponse();
        return ResponseEntity.ok()
                .headers(response.metadata().headerValues().toHttpHeaders())
                .body(response.metadata());
    }
}
