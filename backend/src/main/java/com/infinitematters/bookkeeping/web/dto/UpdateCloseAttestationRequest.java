package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UpdateCloseAttestationRequest(
        @NotBlank String month,
        UUID closeOwnerUserId,
        UUID closeApproverUserId,
        String summary) {
}
