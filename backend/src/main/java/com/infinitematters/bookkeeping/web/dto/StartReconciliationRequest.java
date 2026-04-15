package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record StartReconciliationRequest(
        @NotBlank String month,
        @NotNull UUID financialAccountId,
        @NotNull @DecimalMin("0.00") BigDecimal openingBalance,
        @NotNull @DecimalMin("0.00") BigDecimal statementEndingBalance) {
}
