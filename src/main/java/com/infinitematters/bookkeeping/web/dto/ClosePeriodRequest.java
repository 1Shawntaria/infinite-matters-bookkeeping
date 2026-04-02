package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ClosePeriodRequest(@NotBlank String month) {
}
