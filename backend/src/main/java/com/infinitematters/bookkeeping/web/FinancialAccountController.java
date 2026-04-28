package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.accounts.FinancialAccountService;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.web.dto.CreateFinancialAccountRequest;
import com.infinitematters.bookkeeping.web.dto.FinancialAccountResponse;
import com.infinitematters.bookkeeping.web.dto.UpdateFinancialAccountRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class FinancialAccountController {
    private final FinancialAccountService financialAccountService;
    private final TenantAccessService tenantAccessService;
    private final AuditService auditService;

    public FinancialAccountController(FinancialAccountService financialAccountService,
                                      TenantAccessService tenantAccessService,
                                      AuditService auditService) {
        this.financialAccountService = financialAccountService;
        this.tenantAccessService = tenantAccessService;
        this.auditService = auditService;
    }

    @PostMapping
    public FinancialAccountResponse create(@Valid @RequestBody CreateFinancialAccountRequest request) {
        tenantAccessService.requireRole(request.organizationId(), Set.of(UserRole.OWNER, UserRole.ADMIN));
        var account = financialAccountService.create(
                request.organizationId(),
                request.name(),
                request.accountType(),
                request.institutionName(),
                request.currency().toUpperCase());
        auditService.record(request.organizationId(), "ACCOUNT_CREATED", "financial_account", account.getId().toString(),
                "Created account " + account.getName());
        return FinancialAccountResponse.from(account);
    }

    @GetMapping
    public List<FinancialAccountResponse> list(@RequestParam UUID organizationId) {
        tenantAccessService.requireAccess(organizationId);
        return financialAccountService.listByOrganization(organizationId)
                .stream()
                .map(FinancialAccountResponse::from)
                .toList();
    }

    @PatchMapping("/{accountId}")
    public FinancialAccountResponse update(@PathVariable UUID accountId,
                                           @RequestParam UUID organizationId,
                                           @Valid @RequestBody UpdateFinancialAccountRequest request) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        var account = financialAccountService.update(
                accountId,
                organizationId,
                request.name(),
                request.institutionName(),
                request.active());
        auditService.record(
                organizationId,
                "ACCOUNT_UPDATED",
                "financial_account",
                account.getId().toString(),
                "Updated account " + account.getName() + " (active=" + account.isActive() + ")");
        return FinancialAccountResponse.from(account);
    }
}
