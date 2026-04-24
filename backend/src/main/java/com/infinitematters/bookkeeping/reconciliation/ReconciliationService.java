package com.infinitematters.bookkeeping.reconciliation;

import com.infinitematters.bookkeeping.accounts.FinancialAccount;
import com.infinitematters.bookkeeping.accounts.FinancialAccountService;
import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.transactions.BookkeepingTransaction;
import com.infinitematters.bookkeeping.transactions.BookkeepingTransactionRepository;
import com.infinitematters.bookkeeping.transactions.TransactionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Comparator;
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

    @Transactional(readOnly = true)
    public ReconciliationAccountDetail accountDetail(UUID organizationId, UUID financialAccountId, YearMonth month) {
        organizationService.get(organizationId);
        FinancialAccount account = financialAccountService.get(financialAccountId);
        if (!account.getOrganization().getId().equals(organizationId)) {
            throw new IllegalArgumentException("Financial account does not belong to organization " + organizationId);
        }

        YearMonth focusMonth = resolveFocusMonth(organizationId, financialAccountId, month);
        List<BookkeepingTransaction> transactions = transactionRepository
                .findByOrganizationIdAndFinancialAccountIdAndTransactionDateBetweenOrderByTransactionDateDescCreatedAtDesc(
                        organizationId,
                        financialAccountId,
                        focusMonth.atDay(1),
                        focusMonth.atEndOfMonth());

        ReconciliationSession session = repository.findByFinancialAccountIdAndPeriodStartAndPeriodEnd(
                        financialAccountId,
                        focusMonth.atDay(1),
                        focusMonth.atEndOfMonth())
                .orElse(null);
        ReconciliationSummary summary = session != null ? toSummary(session) : null;

        BigDecimal netActivity = transactionRepository.sumPostedAmountsForAccountInPeriod(
                financialAccountId,
                focusMonth.atDay(1),
                focusMonth.atEndOfMonth(),
                TransactionStatus.POSTED);
        BigDecimal bookEndingBalance = summary != null && summary.openingBalance() != null
                ? summary.openingBalance().add(netActivity)
                : null;
        BigDecimal varianceAmount = summary != null && summary.statementEndingBalance() != null && bookEndingBalance != null
                ? summary.statementEndingBalance().subtract(bookEndingBalance)
                : null;

        long postedTransactionCount = transactions.stream()
                .filter(transaction -> transaction.getStatus() == TransactionStatus.POSTED)
                .count();
        long reviewRequiredCount = transactions.stream()
                .filter(transaction -> transaction.getStatus() != TransactionStatus.POSTED)
                .count();

        return new ReconciliationAccountDetail(
                focusMonth.toString(),
                account.getId(),
                account.getName(),
                account.getInstitutionName(),
                account.getAccountType(),
                account.getCurrency(),
                account.isActive(),
                summary,
                bookEndingBalance,
                varianceAmount,
                postedTransactionCount,
                reviewRequiredCount,
                summary == null && !transactions.isEmpty(),
                summary != null && summary.status() != ReconciliationStatus.COMPLETED,
                reconciliationStatusMessage(summary, reviewRequiredCount, transactions.isEmpty()),
                transactions.stream()
                        .map(transaction -> new ReconciliationTransactionItem(
                                transaction.getId(),
                                transaction.getTransactionDate(),
                                transaction.getAmount(),
                                transaction.getMerchant(),
                                transaction.getMemo(),
                                transaction.getStatus()))
                        .toList());
    }

    private ReconciliationSummary toSummary(ReconciliationSession session) {
        return new ReconciliationSummary(
                session.getId(),
                session.getFinancialAccount().getId(),
                session.getFinancialAccount().getName(),
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

    private YearMonth resolveFocusMonth(UUID organizationId, UUID financialAccountId, YearMonth month) {
        if (month != null) {
            return month;
        }

        return transactionRepository.findFirstByOrganizationIdAndFinancialAccountIdOrderByTransactionDateDescCreatedAtDesc(
                        organizationId,
                        financialAccountId)
                .map(BookkeepingTransaction::getTransactionDate)
                .map(YearMonth::from)
                .or(() -> repository.findByOrganizationIdOrderByPeriodStartDescCreatedAtDesc(organizationId).stream()
                        .filter(session -> session.getFinancialAccount().getId().equals(financialAccountId))
                        .max(Comparator.comparing(ReconciliationSession::getPeriodStart))
                        .map(session -> YearMonth.from(session.getPeriodStart())))
                .orElse(YearMonth.now());
    }

    private String reconciliationStatusMessage(ReconciliationSummary summary,
                                               long reviewRequiredCount,
                                               boolean hasNoTransactions) {
        if (summary == null) {
            if (hasNoTransactions) {
                return "No transaction activity was found for this account in the selected month.";
            }
            return "Enter statement balances to start reconciliation for this account.";
        }

        if (summary.status() == ReconciliationStatus.COMPLETED) {
            return "This account is reconciled for the selected month.";
        }

        if (reviewRequiredCount > 0) {
            return "Resolve outstanding review items, then complete reconciliation.";
        }

        if (summary.varianceAmount() != null && summary.varianceAmount().compareTo(BigDecimal.ZERO) != 0) {
            return "A variance is still open for this reconciliation session.";
        }

        return "Review balances and complete this reconciliation session when they match.";
    }
}
