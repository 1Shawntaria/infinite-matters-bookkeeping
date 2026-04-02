package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.audit.AuditEventSummary;
import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.users.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService auditService;
    private final TenantAccessService tenantAccessService;

    public AuditController(AuditService auditService, TenantAccessService tenantAccessService) {
        this.auditService = auditService;
        this.tenantAccessService = tenantAccessService;
    }

    @GetMapping("/events")
    public List<AuditEventSummary> listEvents(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return auditService.listForOrganization(organizationId);
    }
}
