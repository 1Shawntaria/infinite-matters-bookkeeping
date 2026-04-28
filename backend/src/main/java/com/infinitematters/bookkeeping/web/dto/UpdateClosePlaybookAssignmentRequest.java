package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UpdateClosePlaybookAssignmentRequest(
        @NotBlank String month,
        UUID assigneeUserId,
        UUID approverUserId) {
}
