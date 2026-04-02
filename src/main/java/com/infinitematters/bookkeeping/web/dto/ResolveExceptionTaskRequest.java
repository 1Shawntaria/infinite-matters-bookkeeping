package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveExceptionTaskRequest(@NotBlank String resolutionComment) {
}
