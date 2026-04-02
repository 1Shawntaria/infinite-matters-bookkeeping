package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.notifications.NotificationService;
import com.infinitematters.bookkeeping.notifications.NotificationRequeueResult;
import com.infinitematters.bookkeeping.notifications.NotificationSuppressionService;
import com.infinitematters.bookkeeping.notifications.NotificationSuppressionSummary;
import com.infinitematters.bookkeeping.notifications.NotificationSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterTaskRunResult;
import com.infinitematters.bookkeeping.notifications.DeadLetterEscalationRunResult;
import com.infinitematters.bookkeeping.notifications.DeadLetterQueueSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportTaskOperationsSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportEffectivenessSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceMonitorRunResult;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceMonitorService;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportEscalationService;
import com.infinitematters.bookkeeping.notifications.DeadLetterWorkflowTaskService;
import com.infinitematters.bookkeeping.notifications.ReminderRunResult;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.web.dto.DeadLetterResolutionRequest;
import com.infinitematters.bookkeeping.web.dto.ResolveDeadLetterNoResendRequest;
import com.infinitematters.bookkeeping.web.dto.RetryDeadLetterRequest;
import com.infinitematters.bookkeeping.workflows.ReviewQueueService;
import com.infinitematters.bookkeeping.workflows.WorkflowInboxSummary;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowInboxController {
    private final ReviewQueueService reviewQueueService;
    private final NotificationService notificationService;
    private final DeadLetterWorkflowTaskService deadLetterWorkflowTaskService;
    private final DeadLetterSupportPerformanceMonitorService deadLetterSupportPerformanceMonitorService;
    private final DeadLetterSupportEscalationService deadLetterSupportEscalationService;
    private final NotificationSuppressionService suppressionService;
    private final TenantAccessService tenantAccessService;

    public WorkflowInboxController(ReviewQueueService reviewQueueService,
                                   NotificationService notificationService,
                                   DeadLetterWorkflowTaskService deadLetterWorkflowTaskService,
                                   DeadLetterSupportPerformanceMonitorService deadLetterSupportPerformanceMonitorService,
                                   DeadLetterSupportEscalationService deadLetterSupportEscalationService,
                                   NotificationSuppressionService suppressionService,
                                   TenantAccessService tenantAccessService) {
        this.reviewQueueService = reviewQueueService;
        this.notificationService = notificationService;
        this.deadLetterWorkflowTaskService = deadLetterWorkflowTaskService;
        this.deadLetterSupportPerformanceMonitorService = deadLetterSupportPerformanceMonitorService;
        this.deadLetterSupportEscalationService = deadLetterSupportEscalationService;
        this.suppressionService = suppressionService;
        this.tenantAccessService = tenantAccessService;
    }

    @GetMapping("/inbox")
    public WorkflowInboxSummary inbox(@RequestParam UUID organizationId) {
        UUID userId = tenantAccessService.requireAccess(organizationId);
        return reviewQueueService.inbox(organizationId, userId);
    }

    @GetMapping("/notifications")
    public List<NotificationSummary> notifications(@RequestParam UUID organizationId) {
        tenantAccessService.requireAccess(organizationId);
        return notificationService.listForOrganization(organizationId);
    }

    @GetMapping("/notifications/attention")
    public List<NotificationSummary> attentionNotifications(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return notificationService.operationsSummary(organizationId).attentionNotifications();
    }

    @GetMapping("/notifications/dead-letter")
    public List<NotificationSummary> deadLetterNotifications(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return notificationService.deadLetters(organizationId);
    }

    @GetMapping("/notifications/dead-letter/queue")
    public DeadLetterQueueSummary deadLetterQueue(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return notificationService.deadLetterQueue(organizationId);
    }

    @GetMapping("/notifications/dead-letter/tasks")
    public DeadLetterSupportTaskOperationsSummary deadLetterSupportTasks(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return deadLetterWorkflowTaskService.operationsSummary(organizationId);
    }

    @GetMapping("/notifications/dead-letter/effectiveness")
    public DeadLetterSupportEffectivenessSummary deadLetterSupportEffectiveness(@RequestParam UUID organizationId,
                                                                                @RequestParam(defaultValue = "6") int weeks) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return deadLetterWorkflowTaskService.effectivenessSummary(organizationId, weeks);
    }

    @PostMapping("/notifications/dead-letter/performance/run")
    public DeadLetterSupportPerformanceMonitorRunResult runDeadLetterSupportPerformanceMonitor(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return deadLetterSupportPerformanceMonitorService.syncOrganization(organizationId);
    }

    @PostMapping("/notifications/dead-letter/tasks/run")
    public DeadLetterTaskRunResult runDeadLetterTaskSync(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return deadLetterWorkflowTaskService.syncOrganization(organizationId);
    }

    @PostMapping("/notifications/dead-letter/escalations/run")
    public DeadLetterEscalationRunResult runDeadLetterEscalations(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return deadLetterSupportEscalationService.run(organizationId);
    }

    @GetMapping("/notifications/dead-letter/history")
    public List<NotificationSummary> resolvedDeadLetterNotifications(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return notificationService.resolvedDeadLetters(organizationId);
    }

    @PostMapping("/notifications/{notificationId}/dead-letter/acknowledge")
    public NotificationSummary acknowledgeDeadLetter(@RequestParam UUID organizationId,
                                                     @PathVariable UUID notificationId,
                                                     @RequestBody(required = false) DeadLetterResolutionRequest request) {
        UUID actorUserId = tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return notificationService.acknowledgeDeadLetter(
                organizationId,
                notificationId,
                actorUserId,
                request != null ? request.note() : null);
    }

    @PostMapping("/notifications/{notificationId}/dead-letter/resolve")
    public NotificationSummary resolveDeadLetter(@RequestParam UUID organizationId,
                                                 @PathVariable UUID notificationId,
                                                 @RequestBody(required = false) DeadLetterResolutionRequest request) {
        UUID actorUserId = tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return notificationService.resolveDeadLetter(
                organizationId,
                notificationId,
                actorUserId,
                request != null ? request.note() : null);
    }

    @PostMapping("/notifications/{notificationId}/dead-letter/retry")
    public NotificationSummary retryDeadLetter(@RequestParam UUID organizationId,
                                               @PathVariable UUID notificationId,
                                               @RequestBody(required = false) RetryDeadLetterRequest request) {
        UUID actorUserId = tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return notificationService.retryDeadLetter(
                organizationId,
                notificationId,
                actorUserId,
                request != null ? request.recipientEmail() : null,
                request != null ? request.note() : null);
    }

    @PostMapping("/notifications/{notificationId}/dead-letter/no-resend")
    public NotificationSummary resolveDeadLetterNoResend(@RequestParam UUID organizationId,
                                                         @PathVariable UUID notificationId,
                                                         @RequestBody ResolveDeadLetterNoResendRequest request) {
        UUID actorUserId = tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return notificationService.resolveDeadLetterNoResend(
                organizationId,
                notificationId,
                actorUserId,
                request.reasonCode(),
                request.note());
    }

    @GetMapping("/notifications/suppressions")
    public List<NotificationSuppressionSummary> suppressions(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return suppressionService.listActiveSuppressions(organizationId);
    }

    @PostMapping("/notifications/suppressions/{suppressionId}/deactivate")
    public NotificationSuppressionSummary deactivateSuppression(@RequestParam UUID organizationId,
                                                                @PathVariable UUID suppressionId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return suppressionService.deactivate(organizationId, suppressionId);
    }

    @PostMapping("/notifications/{notificationId}/requeue")
    public NotificationSummary requeueNotification(@RequestParam UUID organizationId,
                                                   @PathVariable UUID notificationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return notificationService.requeueFailedNotification(organizationId, notificationId);
    }

    @PostMapping("/notifications/requeue-failed")
    public NotificationRequeueResult requeueFailedNotifications(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return notificationService.requeueFailedNotifications(organizationId);
    }

    @PostMapping("/reminders/run")
    public ReminderRunResult runReminders(@RequestParam UUID organizationId) {
        tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN));
        return notificationService.generateTaskReminders(organizationId);
    }
}
