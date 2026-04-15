package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignReviewTaskRequest(@NotNull UUID assignedUserId) {
}
