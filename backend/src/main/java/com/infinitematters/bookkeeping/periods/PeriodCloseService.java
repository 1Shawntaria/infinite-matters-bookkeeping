package com.infinitematters.bookkeeping.periods;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.close.CloseChecklistService;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.reconciliation.ReconciliationExceptionService;
import com.infinitematters.bookkeeping.security.AccessDeniedException;
import com.infinitematters.bookkeeping.security.RequestIdentityService;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class PeriodCloseService {
    private final AccountingPeriodRepository repository;
    private final OrganizationService organizationService;
    private final AuditService auditService;
    private final CloseChecklistService closeChecklistService;
    private final RequestIdentityService requestIdentityService;
    private final UserService userService;
    private final ReconciliationExceptionService reconciliationExceptionService;

    public PeriodCloseService(AccountingPeriodRepository repository,
                              OrganizationService organizationService,
                              AuditService auditService,
                              CloseChecklistService closeChecklistService,
                              RequestIdentityService requestIdentityService,
                              UserService userService,
                              ReconciliationExceptionService reconciliationExceptionService) {
        this.repository = repository;
        this.organizationService = organizationService;
        this.auditService = auditService;
        this.closeChecklistService = closeChecklistService;
        this.requestIdentityService = requestIdentityService;
        this.userService = userService;
        this.reconciliationExceptionService = reconciliationExceptionService;
    }

    @Transactional
    public AccountingPeriodSummary closeMonth(UUID organizationId, YearMonth month) {
        closeChecklistService.assertCloseReady(organizationId, month);
        return persistClosedPeriod(organizationId, month, PeriodCloseMethod.CHECKLIST, null);
    }

    @Transactional
    public AccountingPeriodSummary forceCloseMonth(UUID organizationId, YearMonth month, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Force close reason is required");
        }
        AccountingPeriodSummary summary = persistClosedPeriod(organizationId, month, PeriodCloseMethod.OVERRIDE, reason);
        reconciliationExceptionService.forceResolveTasksForPeriod(organizationId, month, reason);
        auditService.record(organizationId, "PERIOD_FORCE_CLOSED", "accounting_period", summary.id().toString(),
                "Force closed period " + month + " with reason: " + reason);
        return summary;
    }

    private AccountingPeriodSummary persistClosedPeriod(UUID organizationId, YearMonth month,
                                                        PeriodCloseMethod closeMethod, String overrideReason) {
        Organization organization = organizationService.get(organizationId);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        AppUser actor = requestIdentityService.currentUserId().map(userService::get).orElse(null);
        AccountingPeriod period = repository.findPeriodContaining(organizationId, start)
                .orElseGet(() -> {
                    AccountingPeriod created = new AccountingPeriod();
                    created.setOrganization(organization);
                    created.setPeriodStart(start);
                    created.setPeriodEnd(end);
                    created.setStatus(AccountingPeriodStatus.OPEN);
                    return created;
                });
        period.setStatus(AccountingPeriodStatus.CLOSED);
        period.setClosedAt(Instant.now());
        period.setCloseMethod(closeMethod);
        period.setOverrideReason(overrideReason);
        period.setOverrideApprovedByUser(closeMethod == PeriodCloseMethod.OVERRIDE ? actor : null);
        period = repository.save(period);
        if (closeMethod == PeriodCloseMethod.CHECKLIST) {
            auditService.record(organizationId, "PERIOD_CLOSED", "accounting_period", period.getId().toString(),
                    "Closed period " + start + " through " + end);
        }
        return toSummary(period);
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriodSummary> listPeriods(UUID organizationId) {
        organizationService.get(organizationId);
        return repository.findByOrganizationIdOrderByPeriodStartDesc(organizationId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public void assertPeriodOpen(UUID organizationId, LocalDate entryDate) {
        repository.findPeriodContaining(organizationId, entryDate)
                .filter(period -> period.getStatus() == AccountingPeriodStatus.CLOSED)
                .ifPresent(period -> {
                    throw new AccessDeniedException("Accounting period is closed for date " + entryDate);
                });
    }

    private AccountingPeriodSummary toSummary(AccountingPeriod period) {
        return new AccountingPeriodSummary(
                period.getId(),
                period.getPeriodStart(),
                period.getPeriodEnd(),
                period.getStatus(),
                period.getCloseMethod(),
                period.getOverrideReason(),
                period.getOverrideApprovedByUser() != null ? period.getOverrideApprovedByUser().getId() : null,
                period.getClosedAt(),
                period.getCreatedAt());
    }
}
