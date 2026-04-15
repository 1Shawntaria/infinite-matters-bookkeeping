package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.ledger.EntrySide;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateAdjustmentEntryRequest(
        @NotNull LocalDate entryDate,
        @NotBlank @Size(max = 500) String description,
        @NotBlank @Size(max = 1000) String adjustmentReason,
        @NotEmpty List<@Valid AdjustmentLineRequest> lines) {

    public record AdjustmentLineRequest(
            @NotBlank @Size(max = 64) String accountCode,
            @NotBlank @Size(max = 120) String accountName,
            @NotNull EntrySide entrySide,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount) {
    }
}
