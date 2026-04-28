package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFinancialAccountRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 120) String institutionName,
        boolean active) {
}
