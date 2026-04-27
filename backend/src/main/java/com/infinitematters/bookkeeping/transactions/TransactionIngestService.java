package com.infinitematters.bookkeeping.transactions;

import com.infinitematters.bookkeeping.accounts.FinancialAccount;
import com.infinitematters.bookkeeping.accounts.FinancialAccountService;
import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.domain.CategorizationResult;
import com.infinitematters.bookkeeping.domain.Transaction;
import com.infinitematters.bookkeeping.ingest.CsvIngestor;
import com.infinitematters.bookkeeping.ingest.Normalizer;
import com.infinitematters.bookkeeping.ledger.LedgerService;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.periods.PeriodCloseService;
import com.infinitematters.bookkeeping.service.CategorizerService;
import com.infinitematters.bookkeeping.workflows.ReviewQueueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionIngestService {
    private static final double AUTO_ACCEPT_THRESHOLD = 0.85;

    private final CsvIngestor csvIngestor;
    private final Normalizer normalizer;
    private final OrganizationService organizationService;
    private final FinancialAccountService financialAccountService;
    private final BookkeepingTransactionRepository transactionRepository;
    private final CategorizationDecisionRepository decisionRepository;
    private final DecisionMemoryService decisionMemoryService;
    private final CategorizerService categorizerService;
    private final ReviewQueueService reviewQueueService;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final PeriodCloseService periodCloseService;

    public TransactionIngestService(CsvIngestor csvIngestor,
                                    Normalizer normalizer,
                                    OrganizationService organizationService,
                                    FinancialAccountService financialAccountService,
                                    BookkeepingTransactionRepository transactionRepository,
                                    CategorizationDecisionRepository decisionRepository,
                                    DecisionMemoryService decisionMemoryService,
                                    CategorizerService categorizerService,
                                    ReviewQueueService reviewQueueService,
                                    LedgerService ledgerService,
                                    AuditService auditService,
                                    PeriodCloseService periodCloseService) {
        this.csvIngestor = csvIngestor;
        this.normalizer = normalizer;
        this.organizationService = organizationService;
        this.financialAccountService = financialAccountService;
        this.transactionRepository = transactionRepository;
        this.decisionRepository = decisionRepository;
        this.decisionMemoryService = decisionMemoryService;
        this.categorizerService = categorizerService;
        this.reviewQueueService = reviewQueueService;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.periodCloseService = periodCloseService;
    }

    @Transactional
    public ImportBatchResult importCsv(UUID organizationId, UUID financialAccountId, MultipartFile file) throws IOException {
        Organization organization = organizationService.get(organizationId);
        FinancialAccount financialAccount = financialAccountService.get(financialAccountId);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty. Upload a file with header id,date,merchant,memo,amount,mcc.");
        }

        if (!financialAccount.getOrganization().getId().equals(organization.getId())) {
            throw new IllegalArgumentException("Financial account does not belong to organization " + organizationId);
        }

        List<Transaction> parsedTransactions = csvIngestor.read(file.getInputStream())
                .stream()
                .map(normalizer::normalize)
                .toList();

        if (parsedTransactions.isEmpty()) {
            throw new IllegalArgumentException("CSV file contained no transaction rows. Expected header id,date,merchant,memo,amount,mcc.");
        }

        List<ImportedTransactionSummary> imported = new ArrayList<>();
        int duplicateCount = 0;
        int reviewRequiredCount = 0;
        int postedCount = 0;

        for (Transaction parsed : parsedTransactions) {
            periodCloseService.assertPeriodOpen(organizationId, parsed.date);
            String fingerprint = fingerprint(financialAccountId, parsed);
            if (transactionRepository.findByOrganizationIdAndSourceFingerprint(organizationId, fingerprint).isPresent()) {
                duplicateCount++;
                continue;
            }

            BookkeepingTransaction transaction = new BookkeepingTransaction();
            transaction.setOrganization(organization);
            transaction.setFinancialAccount(financialAccount);
            transaction.setExternalId(parsed.id);
            transaction.setTransactionDate(parsed.date);
            transaction.setPostedDate(parsed.date);
            transaction.setAmount(parsed.amount);
            transaction.setCurrency(financialAccount.getCurrency());
            transaction.setMerchant(parsed.merchant);
            transaction.setMemo(parsed.memo);
            transaction.setMcc(parsed.mcc);
            transaction.setSourceType("CSV");
            transaction.setSourceFingerprint(fingerprint);
            transaction.setStatus(TransactionStatus.IMPORTED);
            transaction = transactionRepository.save(transaction);

            CategorizationResult result = decisionMemoryService.recall(organizationId, parsed)
                    .orElseGet(() -> categorizerService.categorize(parsed));
            CategorizationDecision decision = new CategorizationDecision();
            decision.setTransaction(transaction);
            decision.setProposedCategory(result.category());
            decision.setFinalCategory(shouldAutoAccept(result) ? result.category() : null);
            decision.setRoute(result.route());
            decision.setConfidenceScore(result.confidence().score());
            decision.setConfidenceReason(result.confidence().rationale());
            decision.setExplanation(result.explanation());
            decision.setStatus(shouldAutoAccept(result) ? DecisionStatus.AUTO_ACCEPTED : DecisionStatus.PENDING_REVIEW);
            decisionRepository.save(decision);

            if (shouldAutoAccept(result)) {
                transaction.setStatus(TransactionStatus.READY_TO_POST);
                ledgerService.ensurePosted(transaction, decision);
                postedCount++;
            } else {
                transaction.setStatus(TransactionStatus.REVIEW_REQUIRED);
                reviewQueueService.createReviewTask(organization, transaction, decision);
                reviewRequiredCount++;
            }

            imported.add(new ImportedTransactionSummary(
                    transaction.getId(),
                    transaction.getFinancialAccount().getId(),
                    transaction.getFinancialAccount().getName(),
                    transaction.getTransactionDate(),
                    transaction.getPostedDate(),
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    transaction.getMerchant(),
                    transaction.getMemo(),
                    transaction.getMcc(),
                    decision.getProposedCategory(),
                    decision.getFinalCategory(),
                    decision.getRoute(),
                    decision.getConfidenceScore(),
                    transaction.getStatus(),
                    transaction.getSourceType(),
                    transaction.getCreatedAt()));

            auditService.record(organizationId, "TRANSACTION_IMPORTED", "transaction", transaction.getId().toString(),
                    "Imported via CSV with route " + decision.getRoute()
                            + ", proposed=" + decision.getProposedCategory()
                            + ", final=" + (decision.getFinalCategory() != null ? decision.getFinalCategory() : "PENDING"));
        }

        return new ImportBatchResult(imported.size(), duplicateCount, reviewRequiredCount, postedCount, imported);
    }

    @Transactional(readOnly = true)
    public List<ImportedTransactionSummary> listTransactions(UUID organizationId) {
        organizationService.get(organizationId);
        return transactionRepository.findByOrganizationIdOrderByTransactionDateDescCreatedAtDesc(organizationId)
                .stream()
                .map(transaction -> {
                    CategorizationDecision decision = decisionRepository.findTopByTransactionIdOrderByCreatedAtDesc(transaction.getId())
                            .orElseThrow(() -> new IllegalStateException("No decision found for transaction " + transaction.getId()));
                    return new ImportedTransactionSummary(
                            transaction.getId(),
                            transaction.getFinancialAccount().getId(),
                            transaction.getFinancialAccount().getName(),
                            transaction.getTransactionDate(),
                            transaction.getPostedDate(),
                            transaction.getAmount(),
                            transaction.getCurrency(),
                            transaction.getMerchant(),
                            transaction.getMemo(),
                            transaction.getMcc(),
                            decision.getProposedCategory(),
                            decision.getFinalCategory(),
                            decision.getRoute(),
                            decision.getConfidenceScore(),
                            transaction.getStatus(),
                            transaction.getSourceType(),
                            transaction.getCreatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ImportedTransactionHistoryItem> listImportHistory(UUID organizationId, UUID financialAccountId) {
        organizationService.get(organizationId);

        List<BookkeepingTransaction> transactions = financialAccountId == null
                ? transactionRepository.findByOrganizationIdOrderByCreatedAtDescIdDesc(organizationId)
                : transactionRepository.findByOrganizationIdAndFinancialAccountIdOrderByCreatedAtDescIdDesc(
                        organizationId,
                        financialAccountId);

        return transactions.stream()
                .map(transaction -> {
                    CategorizationDecision decision = decisionRepository.findTopByTransactionIdOrderByCreatedAtDesc(transaction.getId())
                            .orElseThrow(() -> new IllegalStateException("No decision found for transaction " + transaction.getId()));
                    return new ImportedTransactionHistoryItem(
                            transaction.getId(),
                            transaction.getFinancialAccount().getId(),
                            transaction.getFinancialAccount().getName(),
                            transaction.getCreatedAt(),
                            transaction.getTransactionDate(),
                            transaction.getAmount(),
                            transaction.getMerchant(),
                            toCategoryName(decision.getProposedCategory()),
                            toCategoryName(decision.getFinalCategory()),
                            decision.getRoute(),
                            decision.getConfidenceScore(),
                            transaction.getStatus());
                })
                .toList();
    }

    private String toCategoryName(Enum<?> category) {
        return category == null ? null : category.name();
    }

    private boolean shouldAutoAccept(CategorizationResult result) {
        return "RULES".equals(result.route())
                || "MEMORY".equals(result.route())
                || result.confidence().score() >= AUTO_ACCEPT_THRESHOLD;
    }

    private String fingerprint(UUID accountId, Transaction transaction) {
        String raw = String.join("|",
                accountId.toString(),
                safe(transaction.id),
                safeDate(transaction.date),
                safeAmount(transaction.amount),
                safe(transaction.merchant),
                safe(transaction.memo),
                safe(transaction.mcc));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to compute transaction fingerprint", e);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeDate(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private String safeAmount(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
