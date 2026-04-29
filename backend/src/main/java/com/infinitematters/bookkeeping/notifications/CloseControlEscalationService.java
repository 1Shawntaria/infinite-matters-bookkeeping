package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.workflows.ReviewQueueService;
import com.infinitematters.bookkeeping.workflows.ReviewTaskSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class CloseControlEscalationService {
    static final String REMINDER_REFERENCE_TYPE = "close_control_follow_up";
    static final String ESCALATION_REFERENCE_TYPE = "close_control_follow_up_escalation";
    private static final int REMINDER_THRESHOLD = 2;

    private final ReviewQueueService reviewQueueService;
    private final NotificationRepository notificationRepository;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final AuditService auditService;

    public CloseControlEscalationService(ReviewQueueService reviewQueueService,
                                         NotificationRepository notificationRepository,
                                         OrganizationService organizationService,
                                         UserService userService,
                                         AuditService auditService) {
        this.reviewQueueService = reviewQueueService;
        this.notificationRepository = notificationRepository;
        this.organizationService = organizationService;
        this.userService = userService;
        this.auditService = auditService;
    }

    @Transactional
    public CloseControlEscalationRunResult run(UUID organizationId) {
        organizationService.get(organizationId);
        LocalDate today = LocalDate.now();
        Instant dayStart = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        List<NotificationSummary> created = new ArrayList<>();

        for (ReviewTaskSummary task : reviewQueueService.listCloseControlAttentionTasks(organizationId)) {
            if (!shouldEscalate(organizationId, task)) {
                continue;
            }
            for (AppUser recipient : recipientsFor(organizationId)) {
                if (notificationRepository.existsByOrganizationIdAndUserIdAndReferenceTypeAndReferenceIdAndStatusAndScheduledForAfter(
                        organizationId,
                        recipient.getId(),
                        ESCALATION_REFERENCE_TYPE,
                        task.taskId().toString(),
                        NotificationStatus.SENT,
                        dayStart)) {
                    continue;
                }
                created.add(createEscalation(organizationId, task, recipient, today));
            }
        }

        return new CloseControlEscalationRunResult(created.size(), created);
    }

    private boolean shouldEscalate(UUID organizationId, ReviewTaskSummary task) {
        if (task.assignedToUserId() == null || task.resolvedAt() != null) {
            return false;
        }
        long priorReminders = notificationRepository.countByOrganizationIdAndReferenceTypeAndReferenceId(
                organizationId,
                REMINDER_REFERENCE_TYPE,
                task.taskId().toString());
        return priorReminders >= REMINDER_THRESHOLD;
    }

    private List<AppUser> recipientsFor(UUID organizationId) {
        return userService.membersForOrganizationWithRoles(
                organizationId,
                List.of(UserRole.OWNER, UserRole.ADMIN)).stream()
                .sorted(Comparator.comparing(AppUser::getEmail, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private NotificationSummary createEscalation(UUID organizationId,
                                                 ReviewTaskSummary task,
                                                 AppUser recipient,
                                                 LocalDate today) {
        Notification notification = new Notification();
        notification.setOrganization(organizationService.get(organizationId));
        notification.setCategory(NotificationCategory.WORKFLOW);
        notification.setUser(recipient);
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setStatus(NotificationStatus.SENT);
        notification.setDeliveryState(NotificationDeliveryState.DELIVERED);
        notification.setReferenceType(ESCALATION_REFERENCE_TYPE);
        notification.setReferenceId(task.taskId().toString());
        notification.setRecipientEmail(recipient.getEmail());
        notification.setScheduledFor(Instant.now());
        notification.setSentAt(Instant.now());
        notification.setAttemptCount(0);
        notification.setMessage(buildMessage(task, today));
        notification = notificationRepository.save(notification);
        auditService.record(
                organizationId,
                "CLOSE_CONTROL_ESCALATED",
                "workflow_task",
                task.taskId().toString(),
                "Escalated close-control follow-up to " + recipient.getEmail() + " for " + task.title());
        return NotificationSummary.from(notification);
    }

    private String buildMessage(ReviewTaskSummary task, LocalDate today) {
        String month = extractMonth(task);
        if ("CLOSE_ATTESTATION_FOLLOW_UP".equals(task.taskType())) {
            return "Escalation (" + today + "): attestation for " + month
                    + " still lacks final confirmation after repeated reminders. Owner/admin review is now required.";
        }
        return "Escalation (" + today + "): force-close review for " + month
                + " remains unresolved after repeated reminders. Owner/admin review is now required.";
    }

    private String extractMonth(ReviewTaskSummary task) {
        String actionPath = task.actionPath();
        if (actionPath != null) {
            int monthIndex = actionPath.indexOf("month=");
            if (monthIndex >= 0) {
                return actionPath.substring(monthIndex + "month=".length());
            }
        }
        return task.title();
    }
}
