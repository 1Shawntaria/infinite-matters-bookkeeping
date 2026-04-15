package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskPriority;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskRepository;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskStatus;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterSupportEscalationServiceTests {

    @Mock
    private DeadLetterWorkflowTaskService deadLetterWorkflowTaskService;

    @Mock
    private WorkflowTaskRepository workflowTaskRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    private DeadLetterSupportEscalationService service;

    @BeforeEach
    void setUp() {
        service = new DeadLetterSupportEscalationService(
                deadLetterWorkflowTaskService,
                workflowTaskRepository,
                notificationRepository,
                organizationService,
                userService,
                auditService);
    }

    @Test
    void sendsOneEscalationToAssignedOwnerForStaleTask() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        AppUser assignee = user(userId, "owner@acme.test", "Owner");
        Notification notification = notification(UUID.randomUUID(), organization, "owner@acme.test");
        WorkflowTask task = task(UUID.randomUUID(), organization, notification, assignee);

        DeadLetterSupportTaskSummary summary = new DeadLetterSupportTaskSummary(
                task.getId(),
                notification.getId(),
                DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY,
                "Recipient is currently suppressed by the provider",
                WorkflowTaskPriority.CRITICAL.name(),
                true,
                true,
                false,
                false,
                false,
                3,
                0,
                null,
                LocalDate.now().minusDays(1),
                assignee.getId(),
                assignee.getFullName(),
                "owner@acme.test",
                task.getTitle(),
                task.getDescription());

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(deadLetterWorkflowTaskService.openTaskSummaries(organizationId)).thenReturn(List.of(summary));
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId, WorkflowTaskType.DEAD_LETTER_SUPPORT, WorkflowTaskStatus.OPEN))
                .thenReturn(List.of(task));
        when(userService.get(userId)).thenReturn(assignee);
        when(notificationRepository.existsByWorkflowTaskIdAndUserIdAndReferenceTypeAndStatusAndScheduledForAfter(
                eq(task.getId()), eq(userId), eq("dead_letter_support_escalation"), eq(NotificationStatus.SENT), any()))
                .thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                setId(saved, UUID.randomUUID());
            }
            return saved;
        });

        DeadLetterEscalationRunResult result = service.run(organizationId);

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.notifications()).singleElement()
                .satisfies(created -> {
                    assertThat(created.userId()).isEqualTo(userId);
                    assertThat(created.referenceType()).isEqualTo("dead_letter_support_escalation");
                    assertThat(created.workflowTaskId()).isEqualTo(task.getId());
                    assertThat(created.message()).contains("Escalation");
                });
        verify(auditService).record(
                eq(organizationId),
                eq("DEAD_LETTER_SUPPORT_ESCALATED"),
                eq("workflow_task"),
                eq(task.getId().toString()),
                any());
    }

    @Test
    void sendsEscalationsToOwnersAndAdminsForUnassignedTaskAndSkipsDuplicates() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        AppUser owner = user(UUID.randomUUID(), "owner@acme.test", "Owner");
        AppUser admin = user(UUID.randomUUID(), "admin@acme.test", "Admin");
        Notification notification = notification(UUID.randomUUID(), organization, "member@acme.test");
        WorkflowTask task = task(UUID.randomUUID(), organization, notification, null);

        DeadLetterSupportTaskSummary summary = new DeadLetterSupportTaskSummary(
                task.getId(),
                notification.getId(),
                DeadLetterRecommendedAction.RETRY_DELIVERY,
                "Delivery can be retried after reviewing the destination",
                WorkflowTaskPriority.HIGH.name(),
                false,
                true,
                false,
                false,
                false,
                2,
                0,
                null,
                LocalDate.now().plusDays(1),
                null,
                null,
                "member@acme.test",
                task.getTitle(),
                task.getDescription());

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(deadLetterWorkflowTaskService.openTaskSummaries(organizationId)).thenReturn(List.of(summary));
        when(workflowTaskRepository.findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(
                organizationId, WorkflowTaskType.DEAD_LETTER_SUPPORT, WorkflowTaskStatus.OPEN))
                .thenReturn(List.of(task));
        when(userService.membersForOrganizationWithRoles(organizationId, List.of(UserRole.OWNER, UserRole.ADMIN)))
                .thenReturn(List.of(owner, admin));
        when(notificationRepository.existsByWorkflowTaskIdAndUserIdAndReferenceTypeAndStatusAndScheduledForAfter(
                eq(task.getId()), eq(owner.getId()), eq("dead_letter_support_escalation"), eq(NotificationStatus.SENT), any()))
                .thenReturn(false);
        when(notificationRepository.existsByWorkflowTaskIdAndUserIdAndReferenceTypeAndStatusAndScheduledForAfter(
                eq(task.getId()), eq(admin.getId()), eq("dead_letter_support_escalation"), eq(NotificationStatus.SENT), any()))
                .thenReturn(true);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                setId(saved, UUID.randomUUID());
            }
            return saved;
        });

        DeadLetterEscalationRunResult result = service.run(organizationId);

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.notifications()).singleElement()
                .satisfies(created -> {
                    assertThat(created.userId()).isEqualTo(owner.getId());
                    assertThat(created.message()).contains("No owner is assigned yet");
                });

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUser().getId()).isEqualTo(owner.getId());
        verify(userService, never()).get(any());
        verify(auditService).record(
                eq(organizationId),
                eq("DEAD_LETTER_SUPPORT_ESCALATED"),
                eq("workflow_task"),
                eq(task.getId().toString()),
                any());
    }

    private Organization organization(UUID id) {
        Organization organization = new Organization();
        setId(organization, id);
        return organization;
    }

    private AppUser user(UUID id, String email, String fullName) {
        AppUser user = new AppUser();
        setId(user, id);
        user.setEmail(email);
        user.setFullName(fullName);
        return user;
    }

    private Notification notification(UUID id, Organization organization, String recipientEmail) {
        Notification notification = new Notification();
        setId(notification, id);
        notification.setOrganization(organization);
        notification.setRecipientEmail(recipientEmail);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setStatus(NotificationStatus.FAILED);
        notification.setDeliveryState(NotificationDeliveryState.FAILED);
        notification.setLastError("Provider rejected");
        return notification;
    }

    private WorkflowTask task(UUID id, Organization organization, Notification notification, AppUser assignee) {
        WorkflowTask task = new WorkflowTask();
        setId(task, id);
        task.setOrganization(organization);
        task.setNotification(notification);
        task.setAssignedToUser(assignee);
        task.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT);
        task.setStatus(WorkflowTaskStatus.OPEN);
        task.setPriority(assignee != null ? WorkflowTaskPriority.CRITICAL : WorkflowTaskPriority.HIGH);
        task.setTitle("Dead-letter support task");
        task.setDescription("Investigate failed delivery");
        setCreatedAt(task, Instant.now().minusSeconds(2 * 86400L));
        return task;
    }

    private void setId(Object target, UUID id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void setCreatedAt(WorkflowTask task, Instant createdAt) {
        try {
            Field field = WorkflowTask.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(task, createdAt);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
