package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.close.CloseChecklistService;
import com.infinitematters.bookkeeping.close.CloseChecklistSummary;
import com.infinitematters.bookkeeping.audit.AuditEventSummary;
import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.periods.AccountingPeriodSummary;
import com.infinitematters.bookkeeping.periods.PeriodCloseService;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.web.dto.ClosePeriodRequest;
import com.infinitematters.bookkeeping.web.dto.ForceClosePeriodRequest;
import com.infinitematters.bookkeeping.web.dto.AddCloseNoteRequest;
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
    private final AuditService auditService;

    public PeriodsController(PeriodCloseService periodCloseService,
                             CloseChecklistService closeChecklistService,
                             TenantAccessService tenantAccessService,
                             AuditService auditService) {
        this.periodCloseService = periodCloseService;
        this.closeChecklistService = closeChecklistService;
        this.tenantAccessService = tenantAccessService;
        this.auditService = auditService;
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

    @GetMapping("/notes")
    public List<AuditEventSummary> notes(@RequestParam UUID organizationId,
                                         @RequestParam String month) {
        tenantAccessService.requireAccess(organizationId);
        return auditService.listForOrganizationByEventTypeAndEntity(
                organizationId,
                "PERIOD_CLOSE_NOTE_ADDED",
                month);
    }

    @PostMapping("/notes")
    public AuditEventSummary addNote(@RequestParam UUID organizationId,
                                     @Valid @RequestBody AddCloseNoteRequest request) {
        tenantAccessService.requireAccess(organizationId);
        auditService.record(
                organizationId,
                "PERIOD_CLOSE_NOTE_ADDED",
                "accounting_period",
                request.month(),
                request.note().trim());
        return auditService.listForOrganizationByEventTypeAndEntity(
                        organizationId,
                        "PERIOD_CLOSE_NOTE_ADDED",
                        request.month())
                .stream()
                .findFirst()
                .orElseThrow();
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
