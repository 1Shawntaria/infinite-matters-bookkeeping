package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.dashboard.DashboardService;
import com.infinitematters.bookkeeping.dashboard.DashboardSnapshot;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;
    private final TenantAccessService tenantAccessService;

    public DashboardController(DashboardService dashboardService,
                               TenantAccessService tenantAccessService) {
        this.dashboardService = dashboardService;
        this.tenantAccessService = tenantAccessService;
    }

    @GetMapping("/snapshot")
    public DashboardSnapshot snapshot(@RequestParam UUID organizationId) {
        UUID userId = tenantAccessService.requireAccess(organizationId);
        return dashboardService.snapshot(organizationId, userId);
    }
}
