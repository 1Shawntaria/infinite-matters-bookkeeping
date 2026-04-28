package com.infinitematters.bookkeeping.periods;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.close.CloseChecklistService;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.organization.PeriodClosePlaybookItemService;
import com.infinitematters.bookkeeping.reconciliation.ReconciliationExceptionService;
import com.infinitematters.bookkeeping.security.AccessDeniedException;
import com.infinitematters.bookkeeping.security.RequestIdentityService;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.web.dto.CloseAttestationResponse;
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
    private final PeriodClosePlaybookItemService periodClosePlaybookItemService;

    public PeriodCloseService(AccountingPeriodRepository repository,
                              OrganizationService organizationService,
                              AuditService auditService,
                              CloseChecklistService closeChecklistService,
                              RequestIdentityService requestIdentityService,
                              UserService userService,
                              ReconciliationExceptionService reconciliationExceptionService,
                              PeriodClosePlaybookItemService periodClosePlaybookItemService) {
        this.repository = repository;
        this.organizationService = organizationService;
        this.auditService = auditService;
        this.closeChecklistService = closeChecklistService;
        this.requestIdentityService = requestIdentityService;
        this.userService = userService;
        this.reconciliationExceptionService = reconciliationExceptionService;
        this.periodClosePlaybookItemService = periodClosePlaybookItemService;
    }

    @Transactional
    public AccountingPeriodSummary closeMonth(UUID organizationId, YearMonth month) {
        closeChecklistService.assertCloseReady(organizationId, month);
        assertApprovalPolicySatisfied(organizationId, month);
        assertAttestationSatisfied(organizationId, month);
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

    @Transactional(readOnly = true)
    public CloseAttestationResponse getCloseAttestation(UUID organizationId, YearMonth month) {
        organizationService.get(organizationId);
        AccountingPeriod period = repository.findPeriodContaining(organizationId, month.atDay(1)).orElse(null);
        return CloseAttestationResponse.from(period, month);
    }

    @Transactional
    public CloseAttestationResponse updateCloseAttestation(UUID organizationId,
                                                           YearMonth month,
                                                           UUID closeOwnerUserId,
                                                           UUID closeApproverUserId,
                                                           String summary) {
        AccountingPeriod period = loadOrCreatePeriod(organizationId, month);
        AppUser nextOwner = resolveWorkspaceUser(organizationId, closeOwnerUserId);
        AppUser nextApprover = resolveWorkspaceUser(organizationId, closeApproverUserId);
        validateAttestationRouting(nextOwner, nextApprover);
        String normalizedSummary = normalizeAttestationSummary(summary);
        boolean ownerChanged = !sameUser(period.getCloseOwnerUser(), nextOwner);
        boolean approverChanged = !sameUser(period.getCloseApproverUser(), nextApprover);
        boolean summaryChanged = !java.util.Objects.equals(period.getCloseAttestationSummary(), normalizedSummary);

        period.setCloseOwnerUser(nextOwner);
        period.setCloseApproverUser(nextApprover);
        period.setCloseAttestationSummary(normalizedSummary);

        if (ownerChanged || approverChanged || summaryChanged) {
            period.setCloseAttestedAt(null);
            period.setCloseAttestedByUser(null);
        }

        return CloseAttestationResponse.from(repository.save(period), month);
    }

    @Transactional
    public CloseAttestationResponse confirmCloseAttestation(UUID organizationId,
                                                            YearMonth month) {
        AccountingPeriod period = loadOrCreatePeriod(organizationId, month);
        if (period.getCloseOwnerUser() == null) {
            throw new IllegalArgumentException("Assign a close owner before confirming the month-end record");
        }
        if (period.getCloseApproverUser() == null) {
            throw new IllegalArgumentException("Assign a close approver before confirming the month-end record");
        }
        if (period.getCloseOwnerUser().getId().equals(period.getCloseApproverUser().getId())) {
            throw new IllegalArgumentException("Close owner and approver must be different people before confirming the month-end record");
        }
        if (period.getCloseAttestationSummary() == null || period.getCloseAttestationSummary().isBlank()) {
            throw new IllegalArgumentException("Add an attestation summary before confirming the month-end record");
        }
        UUID actorUserId = requestIdentityService.requireUserId();
        if (!period.getCloseApproverUser().getId().equals(actorUserId)) {
            throw new AccessDeniedException("Only the assigned close approver can confirm the month-end attestation");
        }
        period.setCloseAttestedAt(Instant.now());
        period.setCloseAttestedByUser(userService.get(actorUserId));
        return CloseAttestationResponse.from(repository.save(period), month);
    }

    private AccountingPeriodSummary persistClosedPeriod(UUID organizationId, YearMonth month,
                                                        PeriodCloseMethod closeMethod, String overrideReason) {
        AppUser actor = requestIdentityService.currentUserId().map(userService::get).orElse(null);
        AccountingPeriod period = loadOrCreatePeriod(organizationId, month);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
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

    private void assertApprovalPolicySatisfied(UUID organizationId, YearMonth month) {
        Organization organization = organizationService.get(organizationId);
        if (!organization.isRequireSignoffBeforeClose()) {
            return;
        }

        List<UUID> signoffActorIds = auditService.listForOrganizationByEventTypeAndEntity(
                        organizationId,
                        "PERIOD_CLOSE_SIGNED_OFF",
                        month.toString())
                .stream()
                .map(summary -> summary.actorUserId())
                .filter(java.util.Objects::nonNull)
                .toList();

        if (signoffActorIds.size() < organization.getMinimumSignoffCount()) {
            throw new IllegalArgumentException(
                    "Cannot close period until the required number of sign-offs has been recorded");
        }

        if (organization.isRequireOwnerSignoffBeforeClose()) {
            boolean hasOwnerSignoff = signoffActorIds.stream()
                    .anyMatch(actorUserId -> userService.findRoleForOrganization(organizationId, actorUserId)
                            .filter(role -> role.name().equals("OWNER"))
                            .isPresent());
            if (!hasOwnerSignoff) {
                throw new IllegalArgumentException(
                        "Cannot close period until an owner sign-off has been recorded");
            }
        }

        if (!periodClosePlaybookItemService.allRequiredItemsSatisfied(organizationId, month)) {
            throw new IllegalArgumentException(
                    "Cannot close period until the recurring close playbook items for this month are completed");
        }
    }

    private void assertAttestationSatisfied(UUID organizationId, YearMonth month) {
        AccountingPeriod period = repository.findPeriodContaining(organizationId, month.atDay(1))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot close period until the month-end attestation has been confirmed"));
        if (period.getCloseOwnerUser() == null
                || period.getCloseApproverUser() == null
                || period.getCloseAttestedAt() == null
                || period.getCloseAttestedByUser() == null) {
            throw new IllegalArgumentException(
                    "Cannot close period until the month-end attestation has been confirmed");
        }
        if (period.getCloseOwnerUser().getId().equals(period.getCloseApproverUser().getId())) {
            throw new IllegalArgumentException(
                    "Cannot close period until the month-end attestation has a distinct approver");
        }
        if (!period.getCloseApproverUser().getId().equals(period.getCloseAttestedByUser().getId())) {
            throw new IllegalArgumentException(
                    "Cannot close period until the assigned close approver has confirmed the month-end attestation");
        }
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

    private AccountingPeriod loadOrCreatePeriod(UUID organizationId, YearMonth month) {
        Organization organization = organizationService.get(organizationId);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        return repository.findPeriodContaining(organizationId, start)
                .orElseGet(() -> {
                    AccountingPeriod created = new AccountingPeriod();
                    created.setOrganization(organization);
                    created.setPeriodStart(start);
                    created.setPeriodEnd(end);
                    created.setStatus(AccountingPeriodStatus.OPEN);
                    created.setCloseMethod(PeriodCloseMethod.CHECKLIST);
                    return created;
                });
    }

    private AppUser resolveWorkspaceUser(UUID organizationId, UUID userId) {
        if (userId == null) {
            return null;
        }
        if (!userService.hasAccess(organizationId, userId)) {
            throw new IllegalArgumentException("Assigned attestation user does not belong to this organization");
        }
        return userService.get(userId);
    }

    private static void validateAttestationRouting(AppUser owner, AppUser approver) {
        if (owner != null && approver != null && owner.getId().equals(approver.getId())) {
            throw new IllegalArgumentException(
                    "Close owner and approver must be different people for month-end attestation");
        }
    }

    private static String normalizeAttestationSummary(String summary) {
        if (summary == null) {
            return null;
        }
        String normalized = summary.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 2000) {
            throw new IllegalArgumentException("Attestation summary must be 2000 characters or fewer");
        }
        return normalized;
    }

    private static boolean sameUser(AppUser left, AppUser right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.getId().equals(right.getId());
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
