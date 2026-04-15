package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeadLetterSupportEscalationService {
    private static final String ESCALATION_REFERENCE_TYPE = "dead_letter_support_escalation";

    private final DeadLetterWorkflowTaskService deadLetterWorkflowTaskService;
    private final WorkflowTaskRepository workflowTaskRepository;
    private final NotificationRepository notificationRepository;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final AuditService auditService;

    public DeadLetterSupportEscalationService(DeadLetterWorkflowTaskService deadLetterWorkflowTaskService,
                                              WorkflowTaskRepository workflowTaskRepository,
                                              NotificationRepository notificationRepository,
                                              OrganizationService organizationService,
                                              UserService userService,
                                              AuditService auditService) {
        this.deadLetterWorkflowTaskService = deadLetterWorkflowTaskService;
        this.workflowTaskRepository = workflowTaskRepository;
        this.notificationRepository = notificationRepository;
        this.organizationService = organizationService;
        this.userService = userService;
        this.auditService = auditService;
    }

    @Transactional
    public DeadLetterEscalationRunResult run(UUID organizationId) {
        organizationService.get(organizationId);
        LocalDate today = LocalDate.now();
        Instant dayStart = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Map<UUID, WorkflowTask> tasksById = openTasksById(organizationId);
        List<NotificationSummary> created = new ArrayList<>();

        for (DeadLetterSupportTaskSummary summary : deadLetterWorkflowTaskService.openTaskSummaries(organizationId)) {
            if (!summary.overdue() && !summary.stale()) {
                continue;
            }
            WorkflowTask task = tasksById.get(summary.taskId());
            if (task == null) {
                continue;
            }
            for (AppUser recipient : recipientsFor(summary, organizationId)) {
                if (notificationRepository.existsByWorkflowTaskIdAndUserIdAndReferenceTypeAndStatusAndScheduledForAfter(
                        task.getId(),
                        recipient.getId(),
                        ESCALATION_REFERENCE_TYPE,
                        NotificationStatus.SENT,
                        dayStart)) {
                    continue;
                }
                created.add(createEscalationNotification(task, recipient, summary, today));
            }
        }

        return new DeadLetterEscalationRunResult(created.size(), created);
    }

    private Map<UUID, WorkflowTask> openTasksById(UUID organizationId) {
        return workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                        organizationId,
                        com.infinitematters.bookkeeping.workflows.WorkflowTaskType.DEAD_LETTER_SUPPORT,
                        com.infinitematters.bookkeeping.workflows.WorkflowTaskStatus.OPEN)
                .stream()
                .collect(LinkedHashMap::new, (map, task) -> map.put(task.getId(), task), Map::putAll);
    }

    private List<AppUser> recipientsFor(DeadLetterSupportTaskSummary summary, UUID organizationId) {
        if (summary.assignedToUserId() != null) {
            return List.of(userService.get(summary.assignedToUserId()));
        }
        return userService.membersForOrganizationWithRoles(
                organizationId,
                List.of(UserRole.OWNER, UserRole.ADMIN));
    }

    private NotificationSummary createEscalationNotification(WorkflowTask task,
                                                             AppUser recipient,
                                                             DeadLetterSupportTaskSummary summary,
                                                             LocalDate today) {
        Notification notification = new Notification();
        notification.setOrganization(task.getOrganization());
        notification.setWorkflowTask(task);
        notification.setCategory(NotificationCategory.WORKFLOW);
        notification.setUser(recipient);
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setStatus(NotificationStatus.SENT);
        notification.setDeliveryState(NotificationDeliveryState.DELIVERED);
        notification.setReferenceType(ESCALATION_REFERENCE_TYPE);
        notification.setReferenceId(task.getId().toString());
        notification.setRecipientEmail(recipient.getEmail());
        notification.setScheduledFor(Instant.now());
        notification.setSentAt(Instant.now());
        notification.setAttemptCount(0);
        notification.setMessage(buildEscalationMessage(summary, today));
        notification = notificationRepository.save(notification);
        auditService.record(
                task.getOrganization().getId(),
                "DEAD_LETTER_SUPPORT_ESCALATED",
                "workflow_task",
                task.getId().toString(),
                "Escalated to " + recipient.getEmail() + " for task " + task.getTitle());
        return NotificationSummary.from(notification);
    }

    private String buildEscalationMessage(DeadLetterSupportTaskSummary summary, LocalDate today) {
        String ageText = summary.overdue()
                ? "is overdue"
                : "has been open for " + summary.ageDays() + " day" + (summary.ageDays() == 1 ? "" : "s");
        String dueText = summary.dueDate() != null
                ? " Due date: " + summary.dueDate() + "."
                : "";
        String assignmentText = summary.assignedToUserId() != null
                ? " Assigned owner: " + summary.assignedToUserName() + "."
                : " No owner is assigned yet.";
        return "Escalation (" + today + "): dead-letter support task '" + summary.title() + "' " + ageText
                + "." + dueText + assignmentText + " Next action: " + summary.recommendationReason();
    }
}
