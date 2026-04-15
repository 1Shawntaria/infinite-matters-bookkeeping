package com.infinitematters.bookkeeping.close;

import com.infinitematters.bookkeeping.accounts.FinancialAccount;
import com.infinitematters.bookkeeping.accounts.FinancialAccountRepository;
import com.infinitematters.bookkeeping.ledger.JournalEntryRepository;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.reconciliation.ReconciliationService;
import com.infinitematters.bookkeeping.reconciliation.ReconciliationStatus;
import com.infinitematters.bookkeeping.reconciliation.ReconciliationSummary;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskRepository;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskStatus;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CloseChecklistService {
    private final WorkflowTaskRepository workflowTaskRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final OrganizationService organizationService;
    private final ReconciliationService reconciliationService;

    public CloseChecklistService(WorkflowTaskRepository workflowTaskRepository,
                                 FinancialAccountRepository financialAccountRepository,
                                 JournalEntryRepository journalEntryRepository,
                                 OrganizationService organizationService,
                                 ReconciliationService reconciliationService) {
        this.workflowTaskRepository = workflowTaskRepository;
        this.financialAccountRepository = financialAccountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.organizationService = organizationService;
        this.reconciliationService = reconciliationService;
    }

    @Transactional(readOnly = true)
    public CloseChecklistSummary checklist(UUID organizationId, YearMonth month) {
        organizationService.get(organizationId);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        List<CloseChecklistItem> items = new ArrayList<>();

        List<WorkflowTask> openTasks = workflowTaskRepository
                .findByOrganizationIdAndStatusOrderByCreatedAtAsc(organizationId, WorkflowTaskStatus.OPEN)
                .stream()
                .filter(task -> (task.getTaskType() == WorkflowTaskType.RECONCILIATION_EXCEPTION
                                && start.equals(task.getRelatedPeriodStart())
                                && end.equals(task.getRelatedPeriodEnd()))
                        || (task.getTransaction() != null
                        && !task.getTransaction().getTransactionDate().isBefore(start)
                        && !task.getTransaction().getTransactionDate().isAfter(end)))
                .toList();
        items.add(new CloseChecklistItem(
                "OPEN_REVIEW_TASKS",
                "All workflow tasks resolved",
                openTasks.isEmpty(),
                openTasks.isEmpty() ? "No open workflow tasks in period"
                        : openTasks.size() + " open workflow task(s) remain"));

        List<FinancialAccount> activeAccounts = financialAccountRepository.findByOrganizationId(organizationId).stream()
                .filter(FinancialAccount::isActive)
                .toList();
        List<ReconciliationSummary> reconciliations = reconciliationService.list(organizationId, month);

        for (FinancialAccount account : activeAccounts) {
            long entryCount = journalEntryRepository.countTransactionEntriesForAccountInPeriod(account.getId(), start, end);
            boolean requiresReconciliation = entryCount > 0;
            boolean ready = !requiresReconciliation || reconciliations.stream()
                    .anyMatch(summary -> summary.financialAccountId().equals(account.getId())
                            && summary.status() == ReconciliationStatus.COMPLETED);
            String detail = requiresReconciliation
                    ? (ready ? "Reconciled with " + entryCount + " posted entr" + (entryCount == 1 ? "y" : "ies")
                    : "Needs completed reconciliation; " + entryCount + " posted entr" + (entryCount == 1 ? "y" : "ies"))
                    : "No posted activity in period";
            items.add(new CloseChecklistItem(
                    "ACCOUNT_RECONCILIATION",
                    account.getName() + " reconciled",
                    ready,
                    detail));
        }

        boolean ready = items.stream().allMatch(CloseChecklistItem::complete);
        return new CloseChecklistSummary(start, end, ready, items);
    }

    @Transactional(readOnly = true)
    public void assertCloseReady(UUID organizationId, YearMonth month) {
        CloseChecklistSummary summary = checklist(organizationId, month);
        if (!summary.closeReady()) {
            throw new IllegalArgumentException("Cannot close period until checklist is complete");
        }
    }
}
