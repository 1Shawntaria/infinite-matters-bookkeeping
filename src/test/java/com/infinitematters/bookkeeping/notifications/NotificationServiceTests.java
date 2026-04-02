package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserService;
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

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                workflowTaskRepository,
                organizationService,
                suppressionService,
                auditService,
                userService);
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
        when(notificationRepository.existsByWorkflowTaskIdAndStatusAndScheduledForAfter(eq(taskId), eq(NotificationStatus.SENT), any()))
                .thenReturn(true);

        ReminderRunResult result = notificationService.generateTaskReminders(organizationId);

        assertThat(result.createdCount()).isZero();
        verify(notificationRepository).existsByWorkflowTaskIdAndStatusAndScheduledForAfter(eq(taskId), eq(NotificationStatus.SENT), any());
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

    private void setId(Object target, UUID id) {
        try {
            var field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
