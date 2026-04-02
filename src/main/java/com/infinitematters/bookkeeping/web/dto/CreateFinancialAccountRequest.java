package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.accounts.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateFinancialAccountRequest(
        @NotNull UUID organizationId,
        @NotBlank @Size(max = 120) String name,
        @NotNull AccountType accountType,
        @Size(max = 120) String institutionName,
        @NotBlank @Size(min = 3, max = 3) String currency) {
}
