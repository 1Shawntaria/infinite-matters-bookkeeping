package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ForceClosePeriodRequest(
        @NotBlank String month,
        @NotBlank String reason) {
}
