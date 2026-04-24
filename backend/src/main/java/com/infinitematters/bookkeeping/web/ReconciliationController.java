package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.reconciliation.ReconciliationAccountDetail;
import com.infinitematters.bookkeeping.reconciliation.ReconciliationService;
import com.infinitematters.bookkeeping.reconciliation.ReconciliationSummary;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.web.dto.StartReconciliationRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/reconciliations")
public class ReconciliationController {
    private final ReconciliationService reconciliationService;
    private final TenantAccessService tenantAccessService;

    public ReconciliationController(ReconciliationService reconciliationService,
                                    TenantAccessService tenantAccessService) {
        this.reconciliationService = reconciliationService;
        this.tenantAccessService = tenantAccessService;
    }

    @GetMapping
    public List<ReconciliationSummary> list(@RequestParam UUID organizationId,
                                            @RequestParam(required = false) String month) {
        tenantAccessService.requireAccess(organizationId);
        return reconciliationService.list(organizationId, month != null ? YearMonth.parse(month) : null);
    }

    @GetMapping("/accounts/{accountId}")
    public ReconciliationAccountDetail accountDetail(@RequestParam UUID organizationId,
                                                     @PathVariable UUID accountId,
                                                     @RequestParam(required = false) String month) {
        tenantAccessService.requireAccess(organizationId);
        return reconciliationService.accountDetail(
                organizationId,
                accountId,
                month != null ? YearMonth.parse(month) : null);
    }

    @PostMapping
    public ReconciliationSummary start(@RequestParam UUID organizationId,
                                       @Valid @RequestBody StartReconciliationRequest request) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return reconciliationService.start(
                organizationId,
                request.financialAccountId(),
                YearMonth.parse(request.month()),
                request.openingBalance(),
                request.statementEndingBalance());
    }

    @PostMapping("/{sessionId}/complete")
    public ReconciliationSummary complete(@RequestParam UUID organizationId,
                                          @PathVariable UUID sessionId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return reconciliationService.complete(organizationId, sessionId);
    }
}
