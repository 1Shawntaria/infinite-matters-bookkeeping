package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.ledger.LedgerEntrySummary;
import com.infinitematters.bookkeeping.ledger.LedgerAccountReference;
import com.infinitematters.bookkeeping.ledger.LedgerService;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {
    private final LedgerService ledgerService;
    private final TenantAccessService tenantAccessService;

    public LedgerController(LedgerService ledgerService,
                            TenantAccessService tenantAccessService) {
        this.ledgerService = ledgerService;
        this.tenantAccessService = tenantAccessService;
    }

    @GetMapping("/entries")
    public List<LedgerEntrySummary> listEntries(@RequestParam UUID organizationId) {
        tenantAccessService.requireAccess(organizationId);
        return ledgerService.listEntries(organizationId);
    }

    @GetMapping("/accounts")
    public List<LedgerAccountReference> listAccounts(@RequestParam UUID organizationId) {
        tenantAccessService.requireAccess(organizationId);
        return ledgerService.listAccounts(organizationId);
    }
}
