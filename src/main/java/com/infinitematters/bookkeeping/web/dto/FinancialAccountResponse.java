package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.accounts.AccountType;
import com.infinitematters.bookkeeping.accounts.FinancialAccount;

import java.time.Instant;
import java.util.UUID;

public record FinancialAccountResponse(
        UUID id,
        UUID organizationId,
        String name,
        AccountType accountType,
        String institutionName,
        String currency,
        boolean active,
        Instant createdAt) {
    public static FinancialAccountResponse from(FinancialAccount account) {
        return new FinancialAccountResponse(
                account.getId(),
                account.getOrganization().getId(),
                account.getName(),
                account.getAccountType(),
                account.getInstitutionName(),
                account.getCurrency(),
                account.isActive(),
                account.getCreatedAt());
    }
}
