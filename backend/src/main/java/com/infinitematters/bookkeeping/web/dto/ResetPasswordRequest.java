package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(min = 20, max = 512) String token,
        @NotBlank @Size(min = 8, max = 120) String newPassword) {
}
