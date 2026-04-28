package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.close.CloseChecklistService;
import com.infinitematters.bookkeeping.close.CloseChecklistSummary;
import com.infinitematters.bookkeeping.audit.AuditEventSummary;
import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.PeriodClosePlaybookItemService;
import com.infinitematters.bookkeeping.periods.AccountingPeriodSummary;
import com.infinitematters.bookkeeping.periods.PeriodCloseService;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.web.dto.ClosePeriodRequest;
import com.infinitematters.bookkeeping.web.dto.ForceClosePeriodRequest;
import com.infinitematters.bookkeeping.web.dto.AddCloseNoteRequest;
import com.infinitematters.bookkeeping.web.dto.ClosePlaybookItemResponse;
import com.infinitematters.bookkeeping.web.dto.UpdateClosePlaybookAssignmentRequest;
import com.infinitematters.bookkeeping.web.dto.UpdateClosePlaybookStatusRequest;
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
@RequestMapping("/api/periods")
public class PeriodsController {
    private final PeriodCloseService periodCloseService;
    private final CloseChecklistService closeChecklistService;
    private final TenantAccessService tenantAccessService;
    private final AuditService auditService;
    private final PeriodClosePlaybookItemService periodClosePlaybookItemService;

    public PeriodsController(PeriodCloseService periodCloseService,
                             CloseChecklistService closeChecklistService,
                             TenantAccessService tenantAccessService,
                             AuditService auditService,
                             PeriodClosePlaybookItemService periodClosePlaybookItemService) {
        this.periodCloseService = periodCloseService;
        this.closeChecklistService = closeChecklistService;
        this.tenantAccessService = tenantAccessService;
        this.auditService = auditService;
        this.periodClosePlaybookItemService = periodClosePlaybookItemService;
    }

    @GetMapping
    public List<AccountingPeriodSummary> list(@RequestParam UUID organizationId) {
        tenantAccessService.requireAccess(organizationId);
        return periodCloseService.listPeriods(organizationId);
    }

    @GetMapping("/playbook")
    public List<ClosePlaybookItemResponse> playbook(@RequestParam UUID organizationId,
                                                    @RequestParam String month) {
        tenantAccessService.requireAccess(organizationId);
        return periodClosePlaybookItemService.list(organizationId, YearMonth.parse(month))
                .stream()
                .map(ClosePlaybookItemResponse::from)
                .toList();
    }

    @PostMapping("/playbook/{templateItemId}/assignment")
    public ClosePlaybookItemResponse assignPlaybookItem(@RequestParam UUID organizationId,
                                                        @PathVariable UUID templateItemId,
                                                        @Valid @RequestBody UpdateClosePlaybookAssignmentRequest request) {
        UUID actorUserId = tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        var item = periodClosePlaybookItemService.assign(
                organizationId,
                templateItemId,
                YearMonth.parse(request.month()),
                request.assigneeUserId(),
                request.approverUserId());
        auditService.record(
                organizationId,
                "PERIOD_CLOSE_PLAYBOOK_ASSIGNED",
                "organization_close_template_item",
                templateItemId.toString(),
                "Close playbook item routed by user " + actorUserId + " for month " + request.month());
        return ClosePlaybookItemResponse.from(item);
    }

    @PostMapping("/playbook/{templateItemId}/complete")
    public ClosePlaybookItemResponse completePlaybookItem(@RequestParam UUID organizationId,
                                                          @PathVariable UUID templateItemId,
                                                          @Valid @RequestBody UpdateClosePlaybookStatusRequest request) {
        UUID actorUserId = tenantAccessService.requireAccess(organizationId);
        var item = periodClosePlaybookItemService.markComplete(
                organizationId,
                templateItemId,
                YearMonth.parse(request.month()),
                request.marked(),
                actorUserId);
        auditService.record(
                organizationId,
                request.marked() ? "PERIOD_CLOSE_PLAYBOOK_COMPLETED" : "PERIOD_CLOSE_PLAYBOOK_REOPENED",
                "organization_close_template_item",
                templateItemId.toString(),
                "Close playbook item updated by user " + actorUserId + " for month " + request.month());
        return ClosePlaybookItemResponse.from(item);
    }

    @PostMapping("/playbook/{templateItemId}/approve")
    public ClosePlaybookItemResponse approvePlaybookItem(@RequestParam UUID organizationId,
                                                         @PathVariable UUID templateItemId,
                                                         @Valid @RequestBody UpdateClosePlaybookStatusRequest request) {
        UUID actorUserId = tenantAccessService.requireAccess(organizationId);
        var item = periodClosePlaybookItemService.markApproved(
                organizationId,
                templateItemId,
                YearMonth.parse(request.month()),
                request.marked(),
                actorUserId);
        auditService.record(
                organizationId,
                request.marked() ? "PERIOD_CLOSE_PLAYBOOK_APPROVED" : "PERIOD_CLOSE_PLAYBOOK_APPROVAL_CLEARED",
                "organization_close_template_item",
                templateItemId.toString(),
                "Close playbook approval updated by user " + actorUserId + " for month " + request.month());
        return ClosePlaybookItemResponse.from(item);
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

    @GetMapping("/signoffs")
    public List<AuditEventSummary> signoffs(@RequestParam UUID organizationId,
                                            @RequestParam String month) {
        tenantAccessService.requireAccess(organizationId);
        return auditService.listForOrganizationByEventTypeAndEntity(
                organizationId,
                "PERIOD_CLOSE_SIGNED_OFF",
                month);
    }

    @PostMapping("/signoffs")
    public AuditEventSummary addSignoff(@RequestParam UUID organizationId,
                                        @Valid @RequestBody AddCloseNoteRequest request) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        auditService.record(
                organizationId,
                "PERIOD_CLOSE_SIGNED_OFF",
                "accounting_period",
                request.month(),
                request.note().trim());
        return auditService.listForOrganizationByEventTypeAndEntity(
                        organizationId,
                        "PERIOD_CLOSE_SIGNED_OFF",
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
