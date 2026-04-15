package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank @Size(min = 20, max = 512) String refreshToken) {
}
