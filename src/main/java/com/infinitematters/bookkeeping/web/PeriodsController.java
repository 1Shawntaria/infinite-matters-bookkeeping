package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.close.CloseChecklistService;
import com.infinitematters.bookkeeping.close.CloseChecklistSummary;
import com.infinitematters.bookkeeping.periods.AccountingPeriodSummary;
import com.infinitematters.bookkeeping.periods.PeriodCloseService;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.web.dto.ClosePeriodRequest;
import com.infinitematters.bookkeeping.web.dto.ForceClosePeriodRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/periods")
public class PeriodsController {
    private final PeriodCloseService periodCloseService;
    private final CloseChecklistService closeChecklistService;
    private final TenantAccessService tenantAccessService;

    public PeriodsController(PeriodCloseService periodCloseService,
                             CloseChecklistService closeChecklistService,
                             TenantAccessService tenantAccessService) {
        this.periodCloseService = periodCloseService;
        this.closeChecklistService = closeChecklistService;
        this.tenantAccessService = tenantAccessService;
    }

    @GetMapping
    public List<AccountingPeriodSummary> list(@RequestParam UUID organizationId) {
        tenantAccessService.requireAccess(organizationId);
        return periodCloseService.listPeriods(organizationId);
    }

    @GetMapping("/checklist")
    public CloseChecklistSummary checklist(@RequestParam UUID organizationId,
                                           @RequestParam String month) {
        tenantAccessService.requireAccess(organizationId);
        return closeChecklistService.checklist(organizationId, YearMonth.parse(month));
    }

    @PostMapping("/close")
    public AccountingPeriodSummary close(@RequestParam UUID organizationId,
                                         @Valid @RequestBody ClosePeriodRequest request) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return periodCloseService.closeMonth(organizationId, YearMonth.parse(request.month()));
    }

    @PostMapping("/force-close")
    public AccountingPeriodSummary forceClose(@RequestParam UUID organizationId,
                                              @Valid @RequestBody ForceClosePeriodRequest request) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return periodCloseService.forceCloseMonth(organizationId, YearMonth.parse(request.month()), request.reason());
    }
}
