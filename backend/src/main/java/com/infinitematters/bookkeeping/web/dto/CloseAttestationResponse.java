package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.periods.AccountingPeriod;
import com.infinitematters.bookkeeping.users.AppUser;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

public record CloseAttestationResponse(
        UUID accountingPeriodId,
        String month,
        UserSummary closeOwner,
        UserSummary closeApprover,
        String summary,
        Instant attestedAt,
        UserSummary attestedBy,
        boolean attested) {

    public static CloseAttestationResponse from(AccountingPeriod period, YearMonth month) {
        return new CloseAttestationResponse(
                period != null ? period.getId() : null,
                month.toString(),
                summarize(period != null ? period.getCloseOwnerUser() : null),
                summarize(period != null ? period.getCloseApproverUser() : null),
                period != null ? period.getCloseAttestationSummary() : null,
                period != null ? period.getCloseAttestedAt() : null,
                summarize(period != null ? period.getCloseAttestedByUser() : null),
                period != null && period.getCloseAttestedAt() != null);
    }

    private static UserSummary summarize(AppUser user) {
        if (user == null) {
            return null;
        }
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName());
    }

    public record UserSummary(UUID id, String email, String fullName) {
    }
}
