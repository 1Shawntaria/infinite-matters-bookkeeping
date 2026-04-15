package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeSessionRequest(
        @NotBlank @Size(max = 255) String reason) {
}
