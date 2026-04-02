package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.web.dto.AssignReviewTaskRequest;
import com.infinitematters.bookkeeping.web.dto.ResolveExceptionTaskRequest;
import com.infinitematters.bookkeeping.web.dto.ResolveReviewTaskRequest;
import com.infinitematters.bookkeeping.workflows.ReviewQueueService;
import com.infinitematters.bookkeeping.workflows.ReviewResolutionResult;
import com.infinitematters.bookkeeping.workflows.ReviewTaskSummary;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewQueueService reviewQueueService;
    private final TenantAccessService tenantAccessService;

    public ReviewController(ReviewQueueService reviewQueueService,
                            TenantAccessService tenantAccessService) {
        this.reviewQueueService = reviewQueueService;
        this.tenantAccessService = tenantAccessService;
    }

    @GetMapping("/tasks")
    public List<ReviewTaskSummary> list(@RequestParam UUID organizationId) {
        tenantAccessService.requireAccess(organizationId);
        return reviewQueueService.listOpenReviews(organizationId);
    }

    @PostMapping("/tasks/{taskId}/assign")
    public ReviewTaskSummary assign(@RequestParam UUID organizationId,
                                    @PathVariable UUID taskId,
                                    @Valid @RequestBody AssignReviewTaskRequest request) {
        tenantAccessService.requireAccess(organizationId);
        return reviewQueueService.assignTask(organizationId, taskId, request.assignedUserId());
    }

    @PostMapping("/tasks/{taskId}/resolve")
    public ReviewResolutionResult resolve(@RequestParam UUID organizationId,
                                          @PathVariable UUID taskId,
                                          @Valid @RequestBody ResolveReviewTaskRequest request) {
        tenantAccessService.requireAccess(organizationId);
        if (request.finalCategory() == null) {
            return reviewQueueService.acceptSuggestedCategory(organizationId, taskId);
        }
        return reviewQueueService.overrideCategory(organizationId, taskId, request.finalCategory(), request.resolutionComment());
    }

    @PostMapping("/tasks/{taskId}/resolve-exception")
    public ReviewTaskSummary resolveException(@RequestParam UUID organizationId,
                                              @PathVariable UUID taskId,
                                              @Valid @RequestBody ResolveExceptionTaskRequest request) {
        tenantAccessService.requireAccess(organizationId);
        return reviewQueueService.resolveReconciliationException(organizationId, taskId, request.resolutionComment());
    }
}
