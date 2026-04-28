package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddCloseNoteRequest(
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "Month must be in YYYY-MM format")
        String month,
        @NotBlank
        @Size(max = 2000)
        String note) {
}
