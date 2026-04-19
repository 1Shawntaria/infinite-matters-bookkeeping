package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.dashboard.DashboardHomeContractMetadata;
import com.infinitematters.bookkeeping.dashboard.DashboardHomeContractNegotiation;
import com.infinitematters.bookkeeping.dashboard.DashboardHomeResponse;
import com.infinitematters.bookkeeping.dashboard.DashboardHomeSnapshot;
import com.infinitematters.bookkeeping.dashboard.DashboardHomeContractTestFixtures;
import com.infinitematters.bookkeeping.dashboard.DashboardHomeVersionsResponse;
import com.infinitematters.bookkeeping.dashboard.DashboardService;
import com.infinitematters.bookkeeping.security.BearerTokenAuthenticationFilter;
import com.infinitematters.bookkeeping.security.CsrfProtectionFilter;
import com.infinitematters.bookkeeping.security.RequestIdentityFilter;
import com.infinitematters.bookkeeping.security.RequestLoggingFilter;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = DashboardController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        },
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                        BearerTokenAuthenticationFilter.class,
                        CsrfProtectionFilter.class,
                        RequestIdentityFilter.class,
                        RequestLoggingFilter.class
                })
        })
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class DashboardControllerWebMvcTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private TenantAccessService tenantAccessService;

    @Test
    void homeMapsServiceOwnedResponseIntoHeadersAndBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DashboardHomeContractMetadata metadata = metadata();
        DashboardHomeContractNegotiation negotiation = DashboardHomeContractNegotiation.negotiate("v1", false);
        DashboardHomeSnapshot snapshot = new DashboardHomeSnapshot(
                "v1",
                negotiation.snapshot(),
                YearMonth.of(2026, 3),
                BigDecimal.ZERO,
                0,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of());

        when(tenantAccessService.requireAccess(organizationId)).thenReturn(userId);
        when(dashboardService.homeResponse(organizationId, userId, "v1", false))
                .thenReturn(new DashboardHomeResponse(metadata, negotiation, snapshot));

        ResultActions result = mockMvc.perform(get("/api/dashboard/home")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.contract.version").value("v1"))
                .andExpect(jsonPath("$.contract.requestedVersion").value("v1"))
                .andExpect(jsonPath("$.contract.versionSource").value("default"))
                .andExpect(jsonPath("$.focusMonth").value("2026-03"));

        assertHeaders(result, DashboardHomeContractTestFixtures.homeHeadersV1Default());

        verify(dashboardService).homeResponse(organizationId, userId, "v1", false);
    }

    @Test
    void homeMapsExplicitVersionRequestIntoHeaders() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DashboardHomeContractMetadata metadata = metadata();
        DashboardHomeContractNegotiation negotiation = DashboardHomeContractNegotiation.negotiate("v1", true);
        DashboardHomeSnapshot snapshot = new DashboardHomeSnapshot(
                "v1",
                negotiation.snapshot(),
                YearMonth.of(2026, 3),
                BigDecimal.ZERO,
                0,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of());

        when(tenantAccessService.requireAccess(organizationId)).thenReturn(userId);
        when(dashboardService.homeResponse(organizationId, userId, "v1", true))
                .thenReturn(new DashboardHomeResponse(metadata, negotiation, snapshot));

        ResultActions result = mockMvc.perform(get("/api/dashboard/home")
                        .param("organizationId", organizationId.toString())
                        .param("version", "v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract.versionSource").value("requested"));

        assertHeaders(result, DashboardHomeContractTestFixtures.homeHeadersV1Requested());

        verify(dashboardService).homeResponse(organizationId, userId, "v1", true);
    }

    @Test
    void homeVersionsMapsServiceOwnedMetadataIntoHeadersAndBody() throws Exception {
        DashboardHomeContractMetadata metadata = metadata();

        when(dashboardService.homeVersionsResponse())
                .thenReturn(new DashboardHomeVersionsResponse(metadata));

        ResultActions result = mockMvc.perform(get("/api/dashboard/home/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultVersion").value("v1"))
                .andExpect(jsonPath("$.recommendedVersion").value("v1"))
                .andExpect(jsonPath("$.latestVersion").value("v1"))
                .andExpect(jsonPath("$.headerPolicy")
                        .value("X-Dashboard-Home-Default-Version is the server default, X-Dashboard-Home-Recommended-Version is the preferred client target, X-Dashboard-Home-Latest-Version is the newest available contract, and X-Dashboard-Home-Supported-Versions lists all supported versions."))
                .andExpect(jsonPath("$.supportedVersions[0]").value("v1"));

        assertHeaders(result, DashboardHomeContractTestFixtures.versionDiscoveryHeadersV1());

        verify(dashboardService).homeVersionsResponse();
    }

    @Test
    void homeReturnsBadRequestForUnsupportedVersion() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String message = "Unsupported dashboard home version 'v2'. Supported versions: v1.";

        when(tenantAccessService.requireAccess(organizationId)).thenReturn(userId);
        when(dashboardService.homeResponse(organizationId, userId, "v2", true))
                .thenThrow(new IllegalArgumentException(message));

        ResultActions result = mockMvc.perform(get("/api/dashboard/home")
                        .param("organizationId", organizationId.toString())
                        .param("version", "v2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value("/api/dashboard/home"));

        assertNoNegotiatedHomeHeaders(result);
    }

    private DashboardHomeContractMetadata metadata() {
        return DashboardHomeContractTestFixtures.metadataV1();
    }

    private void assertHeaders(ResultActions result, HttpHeaders expectedHeaders) throws Exception {
        for (String headerName : expectedHeaders.keySet()) {
            result.andExpect(response -> org.assertj.core.api.Assertions.assertThat(response.getResponse().getHeader(headerName))
                    .isEqualTo(expectedHeaders.getFirst(headerName)));
        }
    }

    private void assertNoNegotiatedHomeHeaders(ResultActions result) throws Exception {
        assertMissingHeader(result, "X-Dashboard-Home-Version");
        assertMissingHeader(result, "X-Dashboard-Home-Requested-Version");
        assertMissingHeader(result, "X-Dashboard-Home-Version-Source");
        assertMissingHeader(result, "X-Dashboard-Home-Deprecated");
        assertMissingHeader(result, "Deprecation");
        assertMissingHeader(result, "Sunset");
    }

    private void assertMissingHeader(ResultActions result, String headerName) throws Exception {
        result.andExpect(response -> org.assertj.core.api.Assertions.assertThat(response.getResponse().getHeader(headerName))
                .isNull());
    }
}
