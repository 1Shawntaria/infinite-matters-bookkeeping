package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.workflows.ReviewQueueService;
import com.infinitematters.bookkeeping.workflows.ReviewTaskSummary;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskPriority;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskRepository;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private WorkflowTaskRepository workflowTaskRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private NotificationSuppressionService suppressionService;

    @Mock
    private AuditService auditService;

    @Mock
    private UserService userService;

    @Mock
    private ReviewQueueService reviewQueueService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                workflowTaskRepository,
                organizationService,
                suppressionService,
                auditService,
                userService,
                reviewQueueService);
    }

    @Test
    void generatesReminderForHighPriorityAssignedTask() {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Organization organization = new Organization();
        AppUser user = new AppUser();
        WorkflowTask task = new WorkflowTask();
        task.setOrganization(organization);
        task.setAssignedToUser(user);
        task.setTaskType(com.infinitematters.bookkeeping.workflows.WorkflowTaskType.RECONCILIATION_EXCEPTION);
        task.setTitle("Resolve reconciliation variance");
        task.setDueDate(LocalDate.now().plusDays(1));
        task.setPriority(WorkflowTaskPriority.HIGH);
        task.setStatus(WorkflowTaskStatus.OPEN);

        setId(organization, organizationId);
        setId(user, userId);
        setId(task, taskId);

        when(workflowTaskRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(organizationId, WorkflowTaskStatus.OPEN))
                .thenReturn(List.of(task));
        when(reviewQueueService.listCloseControlAttentionTasks(organizationId)).thenReturn(List.of());
        when(notificationRepository.existsByWorkflowTaskIdAndStatusAndScheduledForAfter(eq(taskId), eq(NotificationStatus.SENT), any()))
                .thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            setId(notification, UUID.randomUUID());
            return notification;
        });

        ReminderRunResult result = notificationService.generateTaskReminders(organizationId);

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.notifications()).hasSize(1);
        assertThat(result.notifications().get(0).workflowTaskId()).isEqualTo(taskId);
        assertThat(result.notifications().get(0).userId()).isEqualTo(userId);
        verify(auditService).record(eq(organizationId), eq("TASK_REMINDER_SENT"), eq("notification"), any(), any());
    }

    @Test
    void skipsReminderWhenTaskAlreadyNotifiedToday() {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        Organization organization = new Organization();
        WorkflowTask task = new WorkflowTask();
        task.setOrganization(organization);
        task.setTitle("Review transaction");
        task.setDueDate(LocalDate.now());
        task.setPriority(WorkflowTaskPriority.MEDIUM);
        task.setStatus(WorkflowTaskStatus.OPEN);

        setId(organization, organizationId);
        setId(task, taskId);

        when(workflowTaskRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(organizationId, WorkflowTaskStatus.OPEN))
                .thenReturn(List.of(task));
        when(reviewQueueService.listCloseControlAttentionTasks(organizationId)).thenReturn(List.of());
        when(notificationRepository.existsByWorkflowTaskIdAndStatusAndScheduledForAfter(eq(taskId), eq(NotificationStatus.SENT), any()))
                .thenReturn(true);

        ReminderRunResult result = notificationService.generateTaskReminders(organizationId);

        assertThat(result.createdCount()).isZero();
        verify(notificationRepository).existsByWorkflowTaskIdAndStatusAndScheduledForAfter(eq(taskId), eq(NotificationStatus.SENT), any());
    }

    @Test
    void generatesReminderForReviewedCloseAttestationFollowUp() {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AppUser user = new AppUser();
        setId(user, userId);

        when(workflowTaskRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(organizationId, WorkflowTaskStatus.OPEN))
                .thenReturn(List.of());
        when(reviewQueueService.listCloseControlAttentionTasks(organizationId))
                .thenReturn(List.of(new ReviewTaskSummary(
                        taskId,
                        null,
                        null,
                        "CLOSE_ATTESTATION_FOLLOW_UP",
                        "HIGH",
                        false,
                        "Confirm month-end attestation for 2026-04",
                        "Awaiting final confirmation",
                        LocalDate.now(),
                        userId,
                        "Acme Owner",
                        null,
                        null,
                        null,
                        null,
                        0.0,
                        null,
                        "/close?month=2026-04",
                        null,
                        userId,
                        Instant.now().minusSeconds(60),
                        null,
                        null,
                        null)));
        when(notificationRepository.existsByOrganizationIdAndReferenceTypeAndReferenceIdAndStatusAndScheduledForAfter(
                eq(organizationId),
                eq("close_control_follow_up"),
                eq(taskId.toString()),
                eq(NotificationStatus.SENT),
                any()))
                .thenReturn(false);
        when(userService.get(userId)).thenReturn(user);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            setId(notification, UUID.randomUUID());
            return notification;
        });

        ReminderRunResult result = notificationService.generateTaskReminders(organizationId);

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.notifications()).hasSize(1);
        assertThat(result.notifications().get(0).workflowTaskId()).isNull();
        assertThat(result.notifications().get(0).userId()).isEqualTo(userId);
        assertThat(result.notifications().get(0).message()).contains("final attestation confirmation is still missing for 2026-04");
        verify(auditService).record(eq(organizationId), eq("CLOSE_CONTROL_REMINDER_SENT"), eq("notification"), any(), any());
    }

    @Test
    void suppressesCloseControlReminderDuringEscalationAcknowledgementCooldown() {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Organization organization = new Organization();
        setId(organization, organizationId);
        AppUser user = new AppUser();
        setId(user, userId);
        user.setEmail("owner@acme.test");

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(reviewQueueService.listCloseControlAttentionTasks(organizationId))
                .thenReturn(List.of(new ReviewTaskSummary(
                        taskId,
                        null,
                        null,
                        "CLOSE_ATTESTATION_FOLLOW_UP",
                        "HIGH",
                        false,
                        "Confirm month-end attestation for 2026-04",
                        "Awaiting final confirmation",
                        LocalDate.now(),
                        userId,
                        "Acme Owner",
                        null,
                        null,
                        null,
                        null,
                        0.0,
                        null,
                        "/close?month=2026-04",
                        null,
                        userId,
                        Instant.now().minusSeconds(60),
                        null,
                        null,
                        null)));
        Notification escalation = new Notification();
        escalation.setReferenceType("close_control_follow_up_escalation");
        escalation.setReferenceId(taskId.toString());
        escalation.setCloseControlDisposition(CloseControlDisposition.WAITING_ON_APPROVER);
        when(notificationRepository.findTopByOrganizationIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                organizationId,
                "close_control_follow_up_escalation",
                taskId.toString()))
                .thenReturn(Optional.of(escalation));
        when(auditService.listForOrganizationByEventTypeAndEntity(
                organizationId,
                "CLOSE_CONTROL_ESCALATION_ACKNOWLEDGED",
                taskId.toString()))
                .thenReturn(List.of(new com.infinitematters.bookkeeping.audit.AuditEventSummary(
                        UUID.randomUUID(),
                        organizationId,
                        userId,
                        "CLOSE_CONTROL_ESCALATION_ACKNOWLEDGED",
                        "workflow_task",
                        taskId.toString(),
                        "Reviewed recently",
                        Instant.now().minusSeconds(60))));

        ReminderRunResult result = notificationService.generateTaskReminders(organizationId);

        assertThat(result.createdCount()).isZero();
        assertThat(result.notifications()).isEmpty();
        verify(notificationRepository, never())
                .save(any(Notification.class));
    }

    @Test
    void generatesOwnerReminderForOverrideDocumentationFollowUp() {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();

        AppUser owner = new AppUser();
        setId(owner, ownerUserId);

        when(workflowTaskRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(organizationId, WorkflowTaskStatus.OPEN))
                .thenReturn(List.of());
        when(reviewQueueService.listCloseControlAttentionTasks(organizationId))
                .thenReturn(List.of(new ReviewTaskSummary(
                        taskId,
                        null,
                        null,
                        "FORCE_CLOSE_REVIEW",
                        "MEDIUM",
                        false,
                        "Finish override documentation for 2026-04",
                        "Owner review already confirmed that override support is being documented for 2026-04.",
                        LocalDate.now(),
                        ownerUserId,
                        "Acme Owner",
                        null,
                        null,
                        null,
                        null,
                        0.0,
                        null,
                        "/close?month=2026-04",
                        null,
                        ownerUserId,
                        Instant.now().minusSeconds(60),
                        null,
                        null,
                        null)));
        Notification escalation = new Notification();
        escalation.setReferenceType("close_control_follow_up_escalation");
        escalation.setReferenceId(taskId.toString());
        escalation.setCloseControlDisposition(CloseControlDisposition.OVERRIDE_DOCS_IN_PROGRESS);
        when(notificationRepository.findTopByOrganizationIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                organizationId,
                "close_control_follow_up_escalation",
                taskId.toString()))
                .thenReturn(Optional.of(escalation));
        when(notificationRepository.existsByOrganizationIdAndReferenceTypeAndReferenceIdAndStatusAndScheduledForAfter(
                eq(organizationId),
                eq("close_control_follow_up"),
                eq(taskId.toString()),
                eq(NotificationStatus.SENT),
                any()))
                .thenReturn(false);
        when(userService.get(ownerUserId)).thenReturn(owner);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            setId(notification, UUID.randomUUID());
            return notification;
        });

        ReminderRunResult result = notificationService.generateTaskReminders(organizationId);

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.notifications()).hasSize(1);
        assertThat(result.notifications().get(0).userId()).isEqualTo(ownerUserId);
        assertThat(result.notifications().get(0).closeControlDisposition()).isEqualTo(CloseControlDisposition.OVERRIDE_DOCS_IN_PROGRESS);
        assertThat(result.notifications().get(0).message()).contains("override documentation still needs owner follow-through");
    }

    @Test
    void revisitTomorrowReminderWaitsUntilDueDate() {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();

        when(workflowTaskRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(organizationId, WorkflowTaskStatus.OPEN))
                .thenReturn(List.of());
        when(reviewQueueService.listCloseControlAttentionTasks(organizationId))
                .thenReturn(List.of(new ReviewTaskSummary(
                        taskId,
                        null,
                        null,
                        "CLOSE_ATTESTATION_FOLLOW_UP",
                        "MEDIUM",
                        false,
                        "Revisit close follow-up tomorrow for 2026-04",
                        "Owner review intentionally deferred this close-control follow-up until tomorrow.",
                        LocalDate.now().plusDays(1),
                        ownerUserId,
                        "Acme Owner",
                        null,
                        null,
                        null,
                        null,
                        0.0,
                        null,
                        "/close?month=2026-04",
                        null,
                        ownerUserId,
                        Instant.now().minusSeconds(60),
                        null,
                        null,
                        null)));
        Notification escalation = new Notification();
        escalation.setReferenceType("close_control_follow_up_escalation");
        escalation.setReferenceId(taskId.toString());
        escalation.setCloseControlDisposition(CloseControlDisposition.REVISIT_TOMORROW);
        when(notificationRepository.findTopByOrganizationIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                organizationId,
                "close_control_follow_up_escalation",
                taskId.toString()))
                .thenReturn(Optional.of(escalation));

        ReminderRunResult result = notificationService.generateTaskReminders(organizationId);

        assertThat(result.createdCount()).isZero();
        assertThat(result.notifications()).isEmpty();
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void revisitTomorrowReminderResumesOnDueDate() {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        AppUser owner = new AppUser();
        setId(owner, ownerUserId);

        when(workflowTaskRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(organizationId, WorkflowTaskStatus.OPEN))
                .thenReturn(List.of());
        when(reviewQueueService.listCloseControlAttentionTasks(organizationId))
                .thenReturn(List.of(new ReviewTaskSummary(
                        taskId,
                        null,
                        null,
                        "CLOSE_ATTESTATION_FOLLOW_UP",
                        "MEDIUM",
                        false,
                        "Revisit close follow-up tomorrow for 2026-04",
                        "Owner review intentionally deferred this close-control follow-up until tomorrow.",
                        LocalDate.now(),
                        ownerUserId,
                        "Acme Owner",
                        null,
                        null,
                        null,
                        null,
                        0.0,
                        null,
                        "/close?month=2026-04",
                        null,
                        ownerUserId,
                        Instant.now().minusSeconds(60),
                        null,
                        null,
                        null)));
        Notification escalation = new Notification();
        escalation.setReferenceType("close_control_follow_up_escalation");
        escalation.setReferenceId(taskId.toString());
        escalation.setCloseControlDisposition(CloseControlDisposition.REVISIT_TOMORROW);
        when(notificationRepository.findTopByOrganizationIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                organizationId,
                "close_control_follow_up_escalation",
                taskId.toString()))
                .thenReturn(Optional.of(escalation));
        when(notificationRepository.existsByOrganizationIdAndReferenceTypeAndReferenceIdAndStatusAndScheduledForAfter(
                eq(organizationId),
                eq("close_control_follow_up"),
                eq(taskId.toString()),
                eq(NotificationStatus.SENT),
                any()))
                .thenReturn(false);
        when(userService.get(ownerUserId)).thenReturn(owner);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            setId(notification, UUID.randomUUID());
            return notification;
        });

        ReminderRunResult result = notificationService.generateTaskReminders(organizationId);

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.notifications()).hasSize(1);
        assertThat(result.notifications().get(0).message()).contains("revisit the close-control follow-up for 2026-04");
        assertThat(result.notifications().get(0).closeControlDisposition()).isEqualTo(CloseControlDisposition.REVISIT_TOMORROW);
    }

    @Test
    void suppressesCloseControlReminderForRevisitTomorrowWindow() {
        UUID organizationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(workflowTaskRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(organizationId, WorkflowTaskStatus.OPEN))
                .thenReturn(List.of());
        when(reviewQueueService.listCloseControlAttentionTasks(organizationId))
                .thenReturn(List.of(new ReviewTaskSummary(
                        taskId,
                        null,
                        null,
                        "CLOSE_ATTESTATION_FOLLOW_UP",
                        "HIGH",
                        false,
                        "Confirm month-end attestation for 2026-04",
                        "Awaiting final confirmation",
                        LocalDate.now(),
                        userId,
                        "Acme Owner",
                        null,
                        null,
                        null,
                        null,
                        0.0,
                        null,
                        "/close?month=2026-04",
                        null,
                        userId,
                        Instant.now().minusSeconds(60),
                        null,
                        null,
                        null)));
        Notification escalation = new Notification();
        escalation.setReferenceType("close_control_follow_up_escalation");
        escalation.setReferenceId(taskId.toString());
        escalation.setCloseControlDisposition(CloseControlDisposition.REVISIT_TOMORROW);
        when(notificationRepository.findTopByOrganizationIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                organizationId,
                "close_control_follow_up_escalation",
                taskId.toString()))
                .thenReturn(Optional.of(escalation));
        when(auditService.listForOrganizationByEventTypeAndEntity(
                organizationId,
                "CLOSE_CONTROL_ESCALATION_ACKNOWLEDGED",
                taskId.toString()))
                .thenReturn(List.of(new com.infinitematters.bookkeeping.audit.AuditEventSummary(
                        UUID.randomUUID(),
                        organizationId,
                        userId,
                        "CLOSE_CONTROL_ESCALATION_ACKNOWLEDGED",
                        "workflow_task",
                        taskId.toString(),
                        "Revisit tomorrow",
                        Instant.now().minus(java.time.Duration.ofHours(23)))));

        ReminderRunResult result = notificationService.generateTaskReminders(organizationId);

        assertThat(result.createdCount()).isZero();
        assertThat(result.notifications()).isEmpty();
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void requeuesFailedNotificationForAnotherAttempt() {
        UUID organizationId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        Organization organization = new Organization();
        Notification notification = new Notification();
        setId(organization, organizationId);
        setId(notification, notificationId);
        notification.setOrganization(organization);
        notification.setStatus(NotificationStatus.FAILED);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setMessage("Reset email failed");
        notification.setAttemptCount(3);
        notification.setLastError("provider unavailable");
        notification.setLastAttemptedAt(Instant.now().minusSeconds(60));
        notification.setSentAt(Instant.now().minusSeconds(120));

        when(notificationRepository.findByIdAndOrganizationId(notificationId, organizationId))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        NotificationSummary summary = notificationService.requeueFailedNotification(organizationId, notificationId);

        assertThat(summary.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(summary.deliveryState()).isEqualTo(NotificationDeliveryState.PENDING);
        assertThat(summary.attemptCount()).isZero();
        assertThat(summary.lastError()).isNull();
        assertThat(summary.lastFailureCode()).isNull();
        assertThat(summary.deadLetterResolutionReasonCode()).isNull();
        assertThat(summary.lastAttemptedAt()).isNull();
        assertThat(summary.sentAt()).isNull();
        verify(auditService).record(eq(organizationId), eq("NOTIFICATION_REQUEUED"), eq("notification"), eq(notificationId.toString()), any());
    }

    @Test
    void bulkRequeuesAllFailedNotifications() {
        UUID organizationId = UUID.randomUUID();
        UUID firstNotificationId = UUID.randomUUID();
        UUID secondNotificationId = UUID.randomUUID();

        Organization organization = new Organization();
        setId(organization, organizationId);

        Notification first = new Notification();
        setId(first, firstNotificationId);
        first.setOrganization(organization);
        first.setStatus(NotificationStatus.FAILED);
        first.setAttemptCount(2);
        first.setLastError("smtp timeout");

        Notification second = new Notification();
        setId(second, secondNotificationId);
        second.setOrganization(organization);
        second.setStatus(NotificationStatus.FAILED);
        second.setAttemptCount(1);
        second.setLastError("smtp timeout");

        when(notificationRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, NotificationStatus.FAILED))
                .thenReturn(List.of(first, second));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationRequeueResult result = notificationService.requeueFailedNotifications(organizationId);

        assertThat(result.requeuedCount()).isEqualTo(2);
        assertThat(result.notifications()).hasSize(2);
        assertThat(result.notifications()).allMatch(notification -> notification.status() == NotificationStatus.PENDING);
        assertThat(result.notifications()).allMatch(notification -> notification.deliveryState() == NotificationDeliveryState.PENDING);
        assertThat(result.notifications()).allMatch(notification -> notification.attemptCount() == 0);
        assertThat(result.notifications()).allMatch(notification -> notification.lastFailureCode() == null);
        assertThat(result.notifications()).allMatch(notification -> notification.deadLetterResolutionReasonCode() == null);
    }

    @Test
    void returnsOnlyDeadLetterNotifications() {
        UUID organizationId = UUID.randomUUID();

        Notification permanentFailure = new Notification();
        setId(permanentFailure, UUID.randomUUID());
        permanentFailure.setStatus(NotificationStatus.FAILED);
        permanentFailure.setChannel(NotificationChannel.EMAIL);
        permanentFailure.setDeliveryState(NotificationDeliveryState.FAILED);
        permanentFailure.setLastError("SendGrid rejected request: 400");

        Notification bounced = new Notification();
        setId(bounced, UUID.randomUUID());
        bounced.setStatus(NotificationStatus.FAILED);
        bounced.setChannel(NotificationChannel.EMAIL);
        bounced.setDeliveryState(NotificationDeliveryState.BOUNCED);
        bounced.setLastError("Provider reported bounce");

        when(notificationRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, NotificationStatus.FAILED))
                .thenReturn(List.of(permanentFailure, bounced));

        List<NotificationSummary> deadLetters = notificationService.deadLetters(organizationId);

        assertThat(deadLetters).hasSize(1);
        assertThat(deadLetters.get(0).lastError()).isEqualTo("SendGrid rejected request: 400");
    }

    @Test
    void returnsResolvedDeadLetterHistorySeparately() {
        UUID organizationId = UUID.randomUUID();

        Notification resolvedDeadLetter = new Notification();
        setId(resolvedDeadLetter, UUID.randomUUID());
        resolvedDeadLetter.setStatus(NotificationStatus.FAILED);
        resolvedDeadLetter.setChannel(NotificationChannel.EMAIL);
        resolvedDeadLetter.setDeliveryState(NotificationDeliveryState.FAILED);
        resolvedDeadLetter.setLastError("Provider rejected request");
        resolvedDeadLetter.setDeadLetterResolutionStatus(DeadLetterResolutionStatus.RESOLVED);

        Notification openDeadLetter = new Notification();
        setId(openDeadLetter, UUID.randomUUID());
        openDeadLetter.setStatus(NotificationStatus.FAILED);
        openDeadLetter.setChannel(NotificationChannel.EMAIL);
        openDeadLetter.setDeliveryState(NotificationDeliveryState.FAILED);
        openDeadLetter.setLastError("Timeout");

        when(notificationRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, NotificationStatus.FAILED))
                .thenReturn(List.of(resolvedDeadLetter, openDeadLetter));

        List<NotificationSummary> resolvedDeadLetters = notificationService.resolvedDeadLetters(organizationId);

        assertThat(resolvedDeadLetters).hasSize(1);
        assertThat(resolvedDeadLetters.get(0).deadLetterResolutionStatus()).isEqualTo(DeadLetterResolutionStatus.RESOLVED);
    }

    @Test
    void acknowledgesAndResolvesDeadLetterNotification() {
        UUID organizationId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();

        Organization organization = new Organization();
        setId(organization, organizationId);

        AppUser actor = new AppUser();
        setId(actor, actorUserId);

        Notification notification = new Notification();
        setId(notification, notificationId);
        notification.setOrganization(organization);
        notification.setStatus(NotificationStatus.FAILED);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setDeliveryState(NotificationDeliveryState.FAILED);
        notification.setLastError("Provider rejected request");

        when(notificationRepository.findByIdAndOrganizationId(notificationId, organizationId))
                .thenReturn(Optional.of(notification));
        when(userService.get(actorUserId)).thenReturn(actor);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationSummary acknowledged = notificationService.acknowledgeDeadLetter(
                organizationId,
                notificationId,
                actorUserId,
                "Investigating provider rejection");

        assertThat(acknowledged.deadLetterResolutionStatus()).isEqualTo(DeadLetterResolutionStatus.ACKNOWLEDGED);
        assertThat(acknowledged.deadLetterResolutionReasonCode()).isNull();
        assertThat(acknowledged.deadLetterResolutionNote()).isEqualTo("Investigating provider rejection");
        assertThat(acknowledged.deadLetterResolvedByUserId()).isEqualTo(actorUserId);
        assertThat(acknowledged.deadLetterResolvedAt()).isNotNull();

        NotificationSummary resolved = notificationService.resolveDeadLetter(
                organizationId,
                notificationId,
                actorUserId,
                "Recipient corrected and delivery no longer required");

        assertThat(resolved.deadLetterResolutionStatus()).isEqualTo(DeadLetterResolutionStatus.RESOLVED);
        assertThat(resolved.deadLetterResolutionReasonCode()).isEqualTo(DeadLetterResolutionReasonCode.OTHER);
        assertThat(resolved.deadLetterResolutionNote()).isEqualTo("Recipient corrected and delivery no longer required");
        assertThat(resolved.deadLetterResolvedByUserId()).isEqualTo(actorUserId);
        verify(auditService).record(eq(organizationId), eq("NOTIFICATION_DEAD_LETTER_ACKNOWLEDGED"), eq("notification"), eq(notificationId.toString()), any());
        verify(auditService).record(eq(organizationId), eq("NOTIFICATION_DEAD_LETTER_RESOLVED"), eq("notification"), eq(notificationId.toString()), any());
    }

    @Test
    void retriesDeadLetterWithCorrectedRecipientEmail() {
        UUID organizationId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();

        Organization organization = new Organization();
        setId(organization, organizationId);

        Notification notification = new Notification();
        setId(notification, notificationId);
        notification.setOrganization(organization);
        notification.setStatus(NotificationStatus.FAILED);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setDeliveryState(NotificationDeliveryState.FAILED);
        notification.setLastError("Provider rejected request");
        notification.setRecipientEmail("old@example.test");

        when(notificationRepository.findByIdAndOrganizationId(notificationId, organizationId))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationSummary retried = notificationService.retryDeadLetter(
                organizationId,
                notificationId,
                actorUserId,
                "Corrected@example.test",
                "Fixed typo");

        assertThat(retried.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(retried.deliveryState()).isEqualTo(NotificationDeliveryState.PENDING);
        assertThat(retried.recipientEmail()).isEqualTo("corrected@example.test");
        assertThat(retried.deadLetterResolutionStatus()).isNull();
        assertThat(retried.deadLetterResolutionReasonCode()).isNull();
        verify(auditService).recordForUser(eq(actorUserId), eq(organizationId), eq("NOTIFICATION_DEAD_LETTER_RETRIED"), eq("notification"), eq(notificationId.toString()), any());
    }

    @Test
    void resolvesDeadLetterAsNoResendWithStructuredReason() {
        UUID organizationId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();

        Organization organization = new Organization();
        setId(organization, organizationId);

        AppUser actor = new AppUser();
        setId(actor, actorUserId);

        Notification notification = new Notification();
        setId(notification, notificationId);
        notification.setOrganization(organization);
        notification.setStatus(NotificationStatus.FAILED);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setDeliveryState(NotificationDeliveryState.FAILED);
        notification.setLastError("Provider rejected request");

        when(notificationRepository.findByIdAndOrganizationId(notificationId, organizationId))
                .thenReturn(Optional.of(notification));
        when(userService.get(actorUserId)).thenReturn(actor);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationSummary resolved = notificationService.resolveDeadLetterNoResend(
                organizationId,
                notificationId,
                actorUserId,
                DeadLetterResolutionReasonCode.USER_REQUESTED_NO_RESEND,
                "Customer opted out");

        assertThat(resolved.deadLetterResolutionStatus()).isEqualTo(DeadLetterResolutionStatus.RESOLVED);
        assertThat(resolved.deadLetterResolutionReasonCode()).isEqualTo(DeadLetterResolutionReasonCode.USER_REQUESTED_NO_RESEND);
        assertThat(resolved.deadLetterResolutionNote()).isEqualTo("Customer opted out");
        verify(auditService).recordForUser(eq(actorUserId), eq(organizationId), eq("NOTIFICATION_DEAD_LETTER_NO_RESEND"), eq("notification"), eq(notificationId.toString()), any());
    }

    @Test
    void buildsDeadLetterQueueWithRecommendedActions() {
        UUID organizationId = UUID.randomUUID();

        Notification retryable = new Notification();
        setId(retryable, UUID.randomUUID());
        retryable.setOrganization(new Organization());
        retryable.setStatus(NotificationStatus.FAILED);
        retryable.setChannel(NotificationChannel.EMAIL);
        retryable.setDeliveryState(NotificationDeliveryState.FAILED);
        retryable.setLastError("Provider rejected request");
        retryable.setLastFailureCode("PROVIDER_REJECTED");
        retryable.setRecipientEmail("retry@example.test");

        Notification suppressed = new Notification();
        setId(suppressed, UUID.randomUUID());
        suppressed.setOrganization(new Organization());
        suppressed.setStatus(NotificationStatus.FAILED);
        suppressed.setChannel(NotificationChannel.EMAIL);
        suppressed.setDeliveryState(NotificationDeliveryState.FAILED);
        suppressed.setLastError("Recipient suppressed by provider");
        suppressed.setLastFailureCode("RECIPIENT_SUPPRESSED");
        suppressed.setRecipientEmail("suppressed@example.test");
        suppressed.setProviderName("sendgrid");

        Notification acknowledged = new Notification();
        setId(acknowledged, UUID.randomUUID());
        acknowledged.setOrganization(new Organization());
        acknowledged.setStatus(NotificationStatus.FAILED);
        acknowledged.setChannel(NotificationChannel.EMAIL);
        acknowledged.setDeliveryState(NotificationDeliveryState.FAILED);
        acknowledged.setLastError("Provider rejected request");
        acknowledged.setDeadLetterResolutionStatus(DeadLetterResolutionStatus.ACKNOWLEDGED);
        acknowledged.setRecipientEmail("ack@example.test");

        Notification resolved = new Notification();
        setId(resolved, UUID.randomUUID());
        resolved.setOrganization(new Organization());
        resolved.setStatus(NotificationStatus.FAILED);
        resolved.setChannel(NotificationChannel.EMAIL);
        resolved.setDeliveryState(NotificationDeliveryState.FAILED);
        resolved.setLastError("Provider rejected request");
        resolved.setDeadLetterResolutionStatus(DeadLetterResolutionStatus.RESOLVED);
        resolved.setRecipientEmail("resolved@example.test");

        NotificationSuppressionSummary suppressionSummary = new NotificationSuppressionSummary(
                UUID.randomUUID(),
                "suppressed@example.test",
                "sendgrid",
                "BOUNCED",
                suppressed.getId(),
                Instant.now(),
                Instant.now());

        when(notificationRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, NotificationStatus.FAILED))
                .thenReturn(List.of(retryable, suppressed, acknowledged, resolved));
        when(suppressionService.activeSuppression("retry@example.test", "sendgrid"))
                .thenReturn(Optional.empty());
        when(suppressionService.activeSuppression("suppressed@example.test", "sendgrid"))
                .thenReturn(Optional.of(suppressionSummary));
        when(suppressionService.activeSuppression("ack@example.test", "sendgrid"))
                .thenReturn(Optional.empty());
        when(suppressionService.activeSuppression("resolved@example.test", "sendgrid"))
                .thenReturn(Optional.empty());

        DeadLetterQueueSummary queue = notificationService.deadLetterQueue(organizationId);

        assertThat(queue.needsRetry()).hasSize(1);
        assertThat(queue.needsRetry().get(0).recommendedAction()).isEqualTo(DeadLetterRecommendedAction.RETRY_DELIVERY);
        assertThat(queue.needsUnsuppress()).hasSize(1);
        assertThat(queue.needsUnsuppress().get(0).recommendedAction()).isEqualTo(DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY);
        assertThat(queue.needsUnsuppress().get(0).suppression()).isEqualTo(suppressionSummary);
        assertThat(queue.acknowledged()).hasSize(1);
        assertThat(queue.acknowledged().get(0).recommendedAction()).isEqualTo(DeadLetterRecommendedAction.REVIEW_ACKNOWLEDGED);
        assertThat(queue.recentlyResolved()).hasSize(1);
        assertThat(queue.recentlyResolved().get(0).recommendedAction()).isEqualTo(DeadLetterRecommendedAction.NONE);
    }

    @Test
    void hidesResolvedAndAcknowledgedPerformanceNotificationsFromActiveViews() {
        UUID organizationId = UUID.randomUUID();

        WorkflowTask acknowledgedTask = new WorkflowTask();
        setId(acknowledgedTask, UUID.randomUUID());
        acknowledgedTask.setStatus(WorkflowTaskStatus.OPEN);
        acknowledgedTask.setAcknowledgedAt(Instant.now());

        WorkflowTask resolvedTask = new WorkflowTask();
        setId(resolvedTask, UUID.randomUUID());
        resolvedTask.setStatus(WorkflowTaskStatus.COMPLETED);

        Notification initialAcknowledged = new Notification();
        setId(initialAcknowledged, UUID.randomUUID());
        initialAcknowledged.setReferenceType("dead_letter_support_performance");
        initialAcknowledged.setWorkflowTask(acknowledgedTask);
        initialAcknowledged.setStatus(NotificationStatus.SENT);
        initialAcknowledged.setChannel(NotificationChannel.IN_APP);
        initialAcknowledged.setDeliveryState(NotificationDeliveryState.DELIVERED);
        initialAcknowledged.setMessage("Initial risk");

        Notification escalationAcknowledged = new Notification();
        setId(escalationAcknowledged, UUID.randomUUID());
        escalationAcknowledged.setReferenceType("dead_letter_support_performance_escalation");
        escalationAcknowledged.setWorkflowTask(acknowledgedTask);
        escalationAcknowledged.setStatus(NotificationStatus.SENT);
        escalationAcknowledged.setChannel(NotificationChannel.IN_APP);
        escalationAcknowledged.setDeliveryState(NotificationDeliveryState.DELIVERED);
        escalationAcknowledged.setMessage("Escalated risk");

        Notification resolvedInitial = new Notification();
        setId(resolvedInitial, UUID.randomUUID());
        resolvedInitial.setReferenceType("dead_letter_support_performance");
        resolvedInitial.setWorkflowTask(resolvedTask);
        resolvedInitial.setStatus(NotificationStatus.SENT);
        resolvedInitial.setChannel(NotificationChannel.IN_APP);
        resolvedInitial.setDeliveryState(NotificationDeliveryState.DELIVERED);
        resolvedInitial.setMessage("Resolved risk");

        when(notificationRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId))
                .thenReturn(List.of(initialAcknowledged, escalationAcknowledged, resolvedInitial));

        List<NotificationSummary> notifications = notificationService.listForOrganization(organizationId);

        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).id()).isEqualTo(initialAcknowledged.getId());
    }

    @Test
    void includesOnlyUnacknowledgedPerformanceRiskNotificationsInAttentionSummary() {
        UUID organizationId = UUID.randomUUID();

        WorkflowTask openUnacknowledgedTask = new WorkflowTask();
        setId(openUnacknowledgedTask, UUID.randomUUID());
        openUnacknowledgedTask.setStatus(WorkflowTaskStatus.OPEN);

        WorkflowTask acknowledgedTask = new WorkflowTask();
        setId(acknowledgedTask, UUID.randomUUID());
        acknowledgedTask.setStatus(WorkflowTaskStatus.OPEN);
        acknowledgedTask.setAcknowledgedAt(Instant.now());

        Notification activeRisk = new Notification();
        setId(activeRisk, UUID.randomUUID());
        activeRisk.setReferenceType("dead_letter_support_performance");
        activeRisk.setWorkflowTask(openUnacknowledgedTask);
        activeRisk.setStatus(NotificationStatus.SENT);
        activeRisk.setChannel(NotificationChannel.IN_APP);
        activeRisk.setDeliveryState(NotificationDeliveryState.DELIVERED);
        activeRisk.setMessage("Open risk");
        setCreatedAt(activeRisk, Instant.now());

        Notification acknowledgedRisk = new Notification();
        setId(acknowledgedRisk, UUID.randomUUID());
        acknowledgedRisk.setReferenceType("dead_letter_support_performance");
        acknowledgedRisk.setWorkflowTask(acknowledgedTask);
        acknowledgedRisk.setStatus(NotificationStatus.SENT);
        acknowledgedRisk.setChannel(NotificationChannel.IN_APP);
        acknowledgedRisk.setDeliveryState(NotificationDeliveryState.DELIVERED);
        acknowledgedRisk.setMessage("Acknowledged risk");
        setCreatedAt(acknowledgedRisk, Instant.now().minusSeconds(60));

        when(notificationRepository.countByOrganizationIdAndStatus(organizationId, NotificationStatus.PENDING)).thenReturn(0L);
        when(notificationRepository.countByOrganizationIdAndStatus(organizationId, NotificationStatus.FAILED)).thenReturn(0L);
        when(notificationRepository.countByOrganizationIdAndDeliveryState(organizationId, NotificationDeliveryState.BOUNCED)).thenReturn(0L);
        when(notificationRepository.countByOrganizationIdAndDeliveryState(organizationId, NotificationDeliveryState.COMPLAINED)).thenReturn(0L);
        when(notificationRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, NotificationStatus.FAILED))
                .thenReturn(List.of());
        when(notificationRepository.findTop10ByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                organizationId,
                List.of(NotificationStatus.PENDING, NotificationStatus.FAILED)))
                .thenReturn(List.of());
        when(notificationRepository.findByOrganizationIdAndReferenceTypeOrderByCreatedAtDesc(
                organizationId,
                "dead_letter_support_performance"))
                .thenReturn(List.of(activeRisk, acknowledgedRisk));
        when(suppressionService.activeSuppressionCount()).thenReturn(0L);

        NotificationOperationsSummary summary = notificationService.operationsSummary(organizationId);

        assertThat(summary.attentionNotifications()).hasSize(1);
        assertThat(summary.attentionNotifications().get(0).id()).isEqualTo(activeRisk.getId());
    }

    @Test
    void hidesSnoozedPerformanceNotificationsFromAttentionSummary() {
        UUID organizationId = UUID.randomUUID();

        WorkflowTask snoozedTask = new WorkflowTask();
        setId(snoozedTask, UUID.randomUUID());
        snoozedTask.setStatus(WorkflowTaskStatus.OPEN);
        snoozedTask.setSnoozedUntil(LocalDate.now().plusDays(2));

        Notification snoozedRisk = new Notification();
        setId(snoozedRisk, UUID.randomUUID());
        snoozedRisk.setReferenceType("dead_letter_support_performance");
        snoozedRisk.setWorkflowTask(snoozedTask);
        snoozedRisk.setStatus(NotificationStatus.SENT);
        snoozedRisk.setChannel(NotificationChannel.IN_APP);
        snoozedRisk.setDeliveryState(NotificationDeliveryState.DELIVERED);
        setCreatedAt(snoozedRisk, Instant.now());

        when(notificationRepository.countByOrganizationIdAndStatus(organizationId, NotificationStatus.PENDING)).thenReturn(0L);
        when(notificationRepository.countByOrganizationIdAndStatus(organizationId, NotificationStatus.FAILED)).thenReturn(0L);
        when(notificationRepository.countByOrganizationIdAndDeliveryState(organizationId, NotificationDeliveryState.BOUNCED)).thenReturn(0L);
        when(notificationRepository.countByOrganizationIdAndDeliveryState(organizationId, NotificationDeliveryState.COMPLAINED)).thenReturn(0L);
        when(notificationRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, NotificationStatus.FAILED))
                .thenReturn(List.of());
        when(notificationRepository.findTop10ByOrganizationIdAndStatusInOrderByCreatedAtDesc(
                organizationId,
                List.of(NotificationStatus.PENDING, NotificationStatus.FAILED)))
                .thenReturn(List.of());
        when(notificationRepository.findByOrganizationIdAndReferenceTypeOrderByCreatedAtDesc(
                organizationId,
                "dead_letter_support_performance"))
                .thenReturn(List.of(snoozedRisk));
        when(suppressionService.activeSuppressionCount()).thenReturn(0L);

        NotificationOperationsSummary summary = notificationService.operationsSummary(organizationId);

        assertThat(summary.attentionNotifications()).isEmpty();
    }

    private void setId(Object target, UUID id) {
        try {
            var field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void setCreatedAt(Notification notification, Instant createdAt) {
        try {
            var field = Notification.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(notification, createdAt);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
