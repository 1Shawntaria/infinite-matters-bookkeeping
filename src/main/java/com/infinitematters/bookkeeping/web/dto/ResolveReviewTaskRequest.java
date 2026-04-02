package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.domain.Category;
import jakarta.validation.constraints.Size;

public record ResolveReviewTaskRequest(
        Category finalCategory,
        @Size(max = 1000) String resolutionComment) {
}
