package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(min = 8, max = 120) String password) {
}
