package com.infinitematters.bookkeeping.ledger;

import com.infinitematters.bookkeeping.accounts.AccountType;
import com.infinitematters.bookkeeping.accounts.FinancialAccount;
import com.infinitematters.bookkeeping.accounts.FinancialAccountService;
import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.domain.Category;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.periods.PeriodCloseService;
import com.infinitematters.bookkeeping.transactions.BookkeepingTransaction;
import com.infinitematters.bookkeeping.transactions.CategorizationDecision;
import com.infinitematters.bookkeeping.transactions.TransactionStatus;
import com.infinitematters.bookkeeping.web.dto.CreateAdjustmentEntryRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LedgerService {
    private final JournalEntryRepository journalEntryRepository;
    private final JournalEntryLineRepository journalEntryLineRepository;
    private final AuditService auditService;
    private final PeriodCloseService periodCloseService;
    private final OrganizationService organizationService;
    private final FinancialAccountService financialAccountService;

    public LedgerService(JournalEntryRepository journalEntryRepository,
                         JournalEntryLineRepository journalEntryLineRepository,
                         AuditService auditService,
                         PeriodCloseService periodCloseService,
                         OrganizationService organizationService,
                         FinancialAccountService financialAccountService) {
        this.journalEntryRepository = journalEntryRepository;
        this.journalEntryLineRepository = journalEntryLineRepository;
        this.auditService = auditService;
        this.periodCloseService = periodCloseService;
        this.organizationService = organizationService;
        this.financialAccountService = financialAccountService;
    }

    @Transactional
    public JournalEntry ensurePosted(BookkeepingTransaction transaction, CategorizationDecision decision) {
        return journalEntryRepository.findByTransactionId(transaction.getId())
                .orElseGet(() -> createEntry(transaction, decision));
    }

    @Transactional
    public LedgerEntrySummary createAdjustmentEntry(UUID organizationId, CreateAdjustmentEntryRequest request) {
        periodCloseService.assertPeriodOpen(organizationId, request.entryDate());
        validateBalancedLines(request.lines());

        JournalEntry entry = new JournalEntry();
        entry.setOrganization(organizationService.get(organizationId));
        entry.setEntryDate(request.entryDate());
        entry.setDescription(request.description());
        entry.setEntryType(JournalEntryType.ADJUSTMENT);
        entry.setAdjustmentReason(request.adjustmentReason());
        entry = journalEntryRepository.save(entry);

        int order = 1;
        for (CreateAdjustmentEntryRequest.AdjustmentLineRequest requestedLine : request.lines()) {
            JournalEntryLine line = new JournalEntryLine();
            line.setJournalEntry(entry);
            line.setLineOrder(order++);
            line.setAccountCode(requestedLine.accountCode());
            line.setAccountName(requestedLine.accountName());
            line.setEntrySide(requestedLine.entrySide());
            line.setAmount(requestedLine.amount());
            journalEntryLineRepository.save(line);
            entry.getLines().add(line);
        }

        auditService.record(organizationId, "ADJUSTMENT_CREATED", "journal_entry", entry.getId().toString(),
                "Created adjustment entry: " + request.adjustmentReason());
        return toSummary(entry);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntrySummary> listEntries(UUID organizationId) {
        return journalEntryRepository.findByOrganizationIdOrderByEntryDateDescCreatedAtDesc(organizationId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerAccountReference> listAccounts(UUID organizationId) {
        List<LedgerEntrySummary> entries = listEntries(organizationId);
        Map<String, AccountAccumulator> accumulators = entries.stream()
                .flatMap(entry -> entry.lines().stream().map(line -> Map.entry(entry.entryDate(), line)))
                .collect(Collectors.toMap(
                        item -> key(item.getValue().accountCode(), item.getValue().accountName()),
                        item -> AccountAccumulator.fromLedgerLine(item.getValue(), item.getKey()),
                        AccountAccumulator::merge));

        for (Category category : Category.values()) {
            LedgerAccountMapping mapping = LedgerAccountMapping.forCategory(category);
            accumulators.merge(
                    key(mapping.code(), mapping.name()),
                    AccountAccumulator.fromCategory(category, mapping),
                    AccountAccumulator::merge);
        }

        for (FinancialAccount financialAccount : financialAccountService.listByOrganization(organizationId)) {
            LedgerAccountMapping mapping = financialAccountMapping(financialAccount);
            accumulators.merge(
                    key(mapping.code(), mapping.name()),
                    AccountAccumulator.fromFinancialAccount(financialAccount, mapping),
                    AccountAccumulator::merge);
        }

        return accumulators.values().stream()
                .map(AccountAccumulator::toReference)
                .sorted(Comparator
                        .comparing(LedgerAccountReference::accountCode)
                        .thenComparing(LedgerAccountReference::accountName))
                .toList();
    }

    private JournalEntry createEntry(BookkeepingTransaction transaction, CategorizationDecision decision) {
        periodCloseService.assertPeriodOpen(transaction.getOrganization().getId(), transaction.getTransactionDate());
        Category finalCategory = decision.getFinalCategory() != null ? decision.getFinalCategory() : decision.getProposedCategory();
        LedgerAccountMapping categoryAccount = LedgerAccountMapping.forCategory(finalCategory);
        LedgerAccountMapping cashAccount = financialAccountMapping(transaction);
        boolean inflow = isInflow(finalCategory, transaction.getAmount());
        BigDecimal amount = transaction.getAmount().abs();

        JournalEntry entry = new JournalEntry();
        entry.setOrganization(transaction.getOrganization());
        entry.setTransaction(transaction);
        entry.setEntryDate(transaction.getTransactionDate());
        entry.setDescription(buildDescription(transaction, finalCategory));
        entry.setEntryType(JournalEntryType.TRANSACTION);
        entry = journalEntryRepository.save(entry);

        JournalEntryLine firstLine = line(entry, 1,
                inflow ? cashAccount : categoryAccount,
                inflow ? EntrySide.DEBIT : EntrySide.DEBIT,
                amount);
        JournalEntryLine secondLine = line(entry, 2,
                inflow ? categoryAccount : cashAccount,
                inflow ? EntrySide.CREDIT : EntrySide.CREDIT,
                amount);

        journalEntryLineRepository.save(firstLine);
        journalEntryLineRepository.save(secondLine);
        entry.getLines().add(firstLine);
        entry.getLines().add(secondLine);

        transaction.setStatus(TransactionStatus.POSTED);
        auditService.record(transaction.getOrganization().getId(), "LEDGER_POSTED", "journal_entry", entry.getId().toString(),
                "Posted transaction " + transaction.getId() + " to category " + finalCategory);
        return entry;
    }

    private LedgerEntrySummary toSummary(JournalEntry entry) {
        return new LedgerEntrySummary(
                entry.getId(),
                entry.getTransaction() != null ? entry.getTransaction().getId() : null,
                entry.getEntryDate(),
                entry.getDescription(),
                entry.getEntryType(),
                entry.getAdjustmentReason(),
                entry.getCreatedAt(),
                entry.getLines().stream()
                        .sorted((left, right) -> Integer.compare(left.getLineOrder(), right.getLineOrder()))
                        .map(line -> new LedgerEntrySummary.LedgerLineSummary(
                                line.getAccountCode(),
                                line.getAccountName(),
                                line.getEntrySide(),
                                line.getAmount()))
                        .toList());
    }

    private void validateBalancedLines(List<CreateAdjustmentEntryRequest.AdjustmentLineRequest> lines) {
        BigDecimal debits = lines.stream()
                .filter(line -> line.entrySide() == EntrySide.DEBIT)
                .map(CreateAdjustmentEntryRequest.AdjustmentLineRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = lines.stream()
                .filter(line -> line.entrySide() == EntrySide.CREDIT)
                .map(CreateAdjustmentEntryRequest.AdjustmentLineRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debits.compareTo(credits) != 0) {
            throw new IllegalArgumentException("Adjustment entry must be balanced");
        }
    }

    private JournalEntryLine line(JournalEntry entry, int order, LedgerAccountMapping account, EntrySide side,
                                  BigDecimal amount) {
        JournalEntryLine line = new JournalEntryLine();
        line.setJournalEntry(entry);
        line.setLineOrder(order);
        line.setAccountCode(account.code());
        line.setAccountName(account.name());
        line.setEntrySide(side);
        line.setAmount(amount);
        return line;
    }

    private LedgerAccountMapping financialAccountMapping(BookkeepingTransaction transaction) {
        return financialAccountMapping(transaction.getFinancialAccount());
    }

    private LedgerAccountMapping financialAccountMapping(FinancialAccount financialAccount) {
        String name = financialAccount.getName();
        AccountType type = financialAccount.getAccountType();
        return switch (type) {
            case BANK -> new LedgerAccountMapping("1000", name);
            case CREDIT_CARD -> new LedgerAccountMapping("2000", name);
            case CASH -> new LedgerAccountMapping("1010", name);
            case LOAN -> new LedgerAccountMapping("2300", name);
        };
    }

    private String key(String accountCode, String accountName) {
        return accountCode + "|" + accountName;
    }

    private static String classificationForCode(String accountCode) {
        if (accountCode == null || accountCode.isBlank()) {
            return "OTHER";
        }
        if (accountCode.startsWith("1")) return "ASSET";
        if (accountCode.startsWith("2")) return "LIABILITY";
        if (accountCode.startsWith("4")) return "REVENUE";
        if (accountCode.startsWith("5") || accountCode.startsWith("6") || accountCode.startsWith("7")) return "EXPENSE";
        return "OTHER";
    }

    private static final class AccountAccumulator {
        private final String accountCode;
        private final String accountName;
        private final String classification;
        private final LinkedHashSet<String> sourceKinds = new LinkedHashSet<>();
        private final LinkedHashSet<String> categoryHints = new LinkedHashSet<>();
        private long activityEntryCount;
        private LocalDate lastEntryDate;
        private BigDecimal debitTotal = BigDecimal.ZERO;
        private BigDecimal creditTotal = BigDecimal.ZERO;

        private AccountAccumulator(String accountCode, String accountName, String classification) {
            this.accountCode = accountCode;
            this.accountName = accountName;
            this.classification = classification;
        }

        static AccountAccumulator fromLedgerLine(
                LedgerEntrySummary.LedgerLineSummary line,
                LocalDate entryDate) {
            AccountAccumulator accumulator = new AccountAccumulator(
                    line.accountCode(),
                    line.accountName(),
                    classificationForCode(line.accountCode()));
            accumulator.sourceKinds.add("LEDGER_ACTIVITY");
            accumulator.activityEntryCount = 1;
            accumulator.lastEntryDate = entryDate;
            if (line.entrySide() == EntrySide.DEBIT) {
                accumulator.debitTotal = accumulator.debitTotal.add(line.amount());
            } else {
                accumulator.creditTotal = accumulator.creditTotal.add(line.amount());
            }
            return accumulator;
        }

        static AccountAccumulator fromCategory(Category category, LedgerAccountMapping mapping) {
            AccountAccumulator accumulator = new AccountAccumulator(
                    mapping.code(),
                    mapping.name(),
                    classificationForCode(mapping.code()));
            accumulator.sourceKinds.add("SYSTEM_CATEGORY");
            accumulator.categoryHints.add(category.name());
            return accumulator;
        }

        static AccountAccumulator fromFinancialAccount(
                FinancialAccount financialAccount,
                LedgerAccountMapping mapping) {
            AccountAccumulator accumulator = new AccountAccumulator(
                    mapping.code(),
                    mapping.name(),
                    classificationForCode(mapping.code()));
            accumulator.sourceKinds.add("FINANCIAL_ACCOUNT");
            accumulator.categoryHints.add(financialAccount.getAccountType().name());
            return accumulator;
        }

        AccountAccumulator merge(AccountAccumulator other) {
            this.sourceKinds.addAll(other.sourceKinds);
            this.categoryHints.addAll(other.categoryHints);
            this.activityEntryCount += other.activityEntryCount;
            if (this.lastEntryDate == null
                    || (other.lastEntryDate != null && other.lastEntryDate.isAfter(this.lastEntryDate))) {
                this.lastEntryDate = other.lastEntryDate;
            }
            this.debitTotal = this.debitTotal.add(other.debitTotal);
            this.creditTotal = this.creditTotal.add(other.creditTotal);
            return this;
        }

        LedgerAccountReference toReference() {
            return new LedgerAccountReference(
                    accountCode,
                    accountName,
                    classification,
                    new ArrayList<>(sourceKinds),
                    new ArrayList<>(categoryHints),
                    activityEntryCount,
                    lastEntryDate,
                    debitTotal,
                    creditTotal);
        }
    }

    private String buildDescription(BookkeepingTransaction transaction, Category category) {
        String merchant = transaction.getMerchant() == null || transaction.getMerchant().isBlank()
                ? "Transaction"
                : transaction.getMerchant();
        return merchant + " posted to " + category;
    }

    private boolean isInflow(Category category, BigDecimal amount) {
        if (category == Category.INCOME) {
            return true;
        }
        if (category == Category.TRANSFER) {
            return amount.signum() >= 0;
        }
        return false;
    }
}
