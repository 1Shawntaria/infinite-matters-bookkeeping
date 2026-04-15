package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.ledger.LedgerEntrySummary;
import com.infinitematters.bookkeeping.ledger.LedgerService;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.web.dto.CreateAdjustmentEntryRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/adjustments")
public class AdjustmentController {
    private final LedgerService ledgerService;
    private final TenantAccessService tenantAccessService;

    public AdjustmentController(LedgerService ledgerService,
                                TenantAccessService tenantAccessService) {
        this.ledgerService = ledgerService;
        this.tenantAccessService = tenantAccessService;
    }

    @PostMapping
    public LedgerEntrySummary create(@RequestParam UUID organizationId,
                                     @Valid @RequestBody CreateAdjustmentEntryRequest request) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return ledgerService.createAdjustmentEntry(organizationId, request);
    }
}
