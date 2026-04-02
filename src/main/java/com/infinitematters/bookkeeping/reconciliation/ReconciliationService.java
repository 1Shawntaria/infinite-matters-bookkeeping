package com.infinitematters.bookkeeping.reconciliation;

import com.infinitematters.bookkeeping.accounts.FinancialAccount;
import com.infinitematters.bookkeeping.accounts.FinancialAccountService;
import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.transactions.BookkeepingTransactionRepository;
import com.infinitematters.bookkeeping.transactions.TransactionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class ReconciliationService {
    private final ReconciliationSessionRepository repository;
    private final FinancialAccountService financialAccountService;
    private final OrganizationService organizationService;
    private final BookkeepingTransactionRepository transactionRepository;
    private final AuditService auditService;
    private final ReconciliationExceptionService reconciliationExceptionService;

    public ReconciliationService(ReconciliationSessionRepository repository,
                                 FinancialAccountService financialAccountService,
                                 OrganizationService organizationService,
                                 BookkeepingTransactionRepository transactionRepository,
                                 AuditService auditService,
                                 ReconciliationExceptionService reconciliationExceptionService) {
        this.repository = repository;
        this.financialAccountService = financialAccountService;
        this.organizationService = organizationService;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
        this.reconciliationExceptionService = reconciliationExceptionService;
    }

    @Transactional
    public ReconciliationSummary start(UUID organizationId, UUID financialAccountId, YearMonth month,
                                       BigDecimal openingBalance, BigDecimal statementEndingBalance) {
        organizationService.get(organizationId);
        FinancialAccount account = financialAccountService.get(financialAccountId);
        if (!account.getOrganization().getId().equals(organizationId)) {
            throw new IllegalArgumentException("Financial account does not belong to organization " + organizationId);
        }
        var start = month.atDay(1);
        var end = month.atEndOfMonth();

        ReconciliationSession session = repository.findByFinancialAccountIdAndPeriodStartAndPeriodEnd(financialAccountId, start, end)
                .orElseGet(() -> {
                    ReconciliationSession created = new ReconciliationSession();
                    created.setOrganization(account.getOrganization());
                    created.setFinancialAccount(account);
                    created.setPeriodStart(start);
                    created.setPeriodEnd(end);
                    return created;
                });
        session.setOpeningBalance(openingBalance);
        session.setStatementEndingBalance(statementEndingBalance);
        session.setStatus(ReconciliationStatus.IN_PROGRESS);
        session.setCompletedAt(null);
        session.setComputedEndingBalance(null);
        session.setVarianceAmount(null);
        session.setNotes(null);
        session = repository.save(session);
        auditService.record(organizationId, "RECONCILIATION_STARTED", "reconciliation_session", session.getId().toString(),
                "Started reconciliation for " + account.getName() + " in " + month);
        return toSummary(session);
    }

    @Transactional
    public ReconciliationSummary complete(UUID organizationId, UUID sessionId) {
        ReconciliationSession session = repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown reconciliation session: " + sessionId));
        if (!session.getOrganization().getId().equals(organizationId)) {
            throw new IllegalArgumentException("Reconciliation session does not belong to organization " + organizationId);
        }

        BigDecimal netActivity = transactionRepository.sumPostedAmountsForAccountInPeriod(
                session.getFinancialAccount().getId(),
                session.getPeriodStart(),
                session.getPeriodEnd(),
                TransactionStatus.POSTED);
        BigDecimal computedEnding = session.getOpeningBalance().add(netActivity);
        BigDecimal variance = session.getStatementEndingBalance().subtract(computedEnding);
        session.setComputedEndingBalance(computedEnding);
        session.setVarianceAmount(variance);
        if (variance.compareTo(BigDecimal.ZERO) != 0) {
            session.setStatus(ReconciliationStatus.IN_PROGRESS);
            session.setNotes("Variance detected; investigate before close");
            session = repository.save(session);
            reconciliationExceptionService.ensureVarianceTask(session);
            auditService.record(organizationId, "RECONCILIATION_VARIANCE_DETECTED", "reconciliation_session",
                    session.getId().toString(), "Expected " + session.getStatementEndingBalance()
                            + " but computed " + computedEnding);
            return toSummary(session);
        }
        session.setStatus(ReconciliationStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        session.setNotes("Balanced successfully");
        session = repository.save(session);
        reconciliationExceptionService.resolveVarianceTasks(session);
        auditService.record(organizationId, "RECONCILIATION_COMPLETED", "reconciliation_session", session.getId().toString(),
                "Completed reconciliation for " + session.getFinancialAccount().getName());
        return toSummary(session);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationSummary> list(UUID organizationId, YearMonth month) {
        organizationService.get(organizationId);
        if (month == null) {
            return repository.findByOrganizationIdOrderByPeriodStartDescCreatedAtDesc(organizationId)
                    .stream().map(this::toSummary).toList();
        }
        return repository.findByOrganizationIdAndPeriodStartAndPeriodEnd(organizationId, month.atDay(1), month.atEndOfMonth())
                .stream().map(this::toSummary).toList();
    }

    private ReconciliationSummary toSummary(ReconciliationSession session) {
        return new ReconciliationSummary(
                session.getId(),
                session.getFinancialAccount().getId(),
                session.getPeriodStart(),
                session.getPeriodEnd(),
                session.getOpeningBalance(),
                session.getStatementEndingBalance(),
                session.getComputedEndingBalance(),
                session.getVarianceAmount(),
                session.getNotes(),
                session.getStatus(),
                session.getCompletedAt(),
                session.getCreatedAt());
    }
}
