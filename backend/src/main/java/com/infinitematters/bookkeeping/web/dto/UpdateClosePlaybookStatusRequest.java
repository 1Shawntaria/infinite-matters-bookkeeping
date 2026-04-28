package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateClosePlaybookStatusRequest(
        @NotBlank String month,
        boolean marked) {
}
