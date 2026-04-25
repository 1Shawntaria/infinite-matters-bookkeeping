package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.transactions.ImportBatchResult;
import com.infinitematters.bookkeeping.transactions.ImportedTransactionHistoryItem;
import com.infinitematters.bookkeeping.transactions.ImportedTransactionSummary;
import com.infinitematters.bookkeeping.transactions.TransactionIngestService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionImportController {
    private final TransactionIngestService transactionIngestService;
    private final TenantAccessService tenantAccessService;

    public TransactionImportController(TransactionIngestService transactionIngestService,
                                       TenantAccessService tenantAccessService) {
        this.transactionIngestService = transactionIngestService;
        this.tenantAccessService = tenantAccessService;
    }

    @PostMapping(path = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportBatchResult importCsv(@RequestParam UUID organizationId,
                                       @RequestParam UUID financialAccountId,
                                       @RequestPart(required = false) MultipartFile file) throws IOException {
        tenantAccessService.requireAccess(organizationId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required. Attach a file in multipart field 'file'.");
        }
        return transactionIngestService.importCsv(organizationId, financialAccountId, file);
    }

    @GetMapping
    public List<ImportedTransactionSummary> list(@RequestParam UUID organizationId) {
        tenantAccessService.requireAccess(organizationId);
        return transactionIngestService.listTransactions(organizationId);
    }

    @GetMapping("/import-history")
    public List<ImportedTransactionHistoryItem> listImportHistory(@RequestParam UUID organizationId,
                                                                  @RequestParam(required = false) UUID financialAccountId) {
        tenantAccessService.requireAccess(organizationId);
        return transactionIngestService.listImportHistory(organizationId, financialAccountId);
    }
}
