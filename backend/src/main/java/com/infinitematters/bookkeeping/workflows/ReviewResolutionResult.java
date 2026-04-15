package com.infinitematters.bookkeeping.workflows;

import com.infinitematters.bookkeeping.domain.Category;
import com.infinitematters.bookkeeping.transactions.TransactionStatus;

import java.util.UUID;

public record ReviewResolutionResult(
        UUID taskId,
        UUID transactionId,
        Category finalCategory,
        TransactionStatus transactionStatus,
        WorkflowTaskStatus taskStatus) {
}
