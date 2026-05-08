package com.infinitematters.bookkeeping.workflows;

import com.infinitematters.bookkeeping.audit.AuditEventSummary;
import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.dashboard.DashboardActionUrgency;
import com.infinitematters.bookkeeping.ledger.LedgerService;
import com.infinitematters.bookkeeping.notifications.CloseControlDisposition;
import com.infinitematters.bookkeeping.notifications.Notification;
import com.infinitematters.bookkeeping.notifications.NotificationRepository;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.periods.AccountingPeriodRepository;
import com.infinitematters.bookkeeping.periods.PeriodCloseService;
import com.infinitematters.bookkeeping.security.RequestIdentityService;
import com.infinitematters.bookkeeping.transactions.CategorizationDecisionRepository;
import com.infinitematters.bookkeeping.users.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewQueueServiceTests {

    @Mock
    private WorkflowTaskRepository taskRepository;
    @Mock
    private CategorizationDecisionRepository decisionRepository;
    @Mock
    private OrganizationService organizationService;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private AuditService auditService;
    @Mock
    private PeriodCloseService periodCloseService;
    @Mock
    private AccountingPeriodRepository accountingPeriodRepository;
    @Mock
    private UserService userService;
    @Mock
    private RequestIdentityService requestIdentityService;
    @Mock
    private NotificationRepository notificationRepository;

    private ReviewQueueService reviewQueueService;

    @BeforeEach
    void setUp() {
        reviewQueueService = new ReviewQueueService(
                taskRepository,
                decisionRepository,
                organizationService,
                ledgerService,
                auditService,
                periodCloseService,
                accountingPeriodRepository,
                userService,
                requestIdentityService,
                notificationRepository);
    }

    @Test
    void inboxRecommendsRoutineApproverFollowThroughWhenNoEscalationExists() {
        UUID organizationId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        String month = "2026-04";
        Instant attestationUpdatedAt = Instant.parse("2026-04-21T12:00:00Z");
        UUID taskId = closeControlTaskId("CLOSE_ATTESTATION_FOLLOW_UP", organizationId, month);

        stubBaseInboxDependencies(organizationId, month, attestationUpdatedAt);
        when(notificationRepository.findTopByOrganizationIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                organizationId,
                "close_control_follow_up_escalation",
                taskId.toString()))
                .thenReturn(Optional.empty());

        WorkflowInboxSummary inbox = reviewQueueService.inbox(organizationId, currentUserId);

        assertThat(inbox.recommendedActionLabel()).isEqualTo("Push approver follow-through");
        assertThat(inbox.recommendedActionKey()).isEqualTo("PUSH_APPROVER_FOLLOW_THROUGH");
        assertThat(inbox.recommendedActionPath()).isEqualTo("/close?month=2026-04");
        assertThat(inbox.recommendedActionUrgency()).isEqualTo(DashboardActionUrgency.HIGH);
        assertThat(inbox.recommendedActionSeverity()).isEqualTo(CloseFollowUpSeverity.ROUTINE);
        assertThat(inbox.attentionTasks()).singleElement().satisfies(task -> {
            assertThat(task.taskId()).isEqualTo(taskId);
            assertThat(task.closeControlSeverity()).isEqualTo(CloseFollowUpSeverity.ROUTINE);
        });
    }

    @Test
    void inboxRecommendsEscalatedApproverFollowThroughWhenEscalationIsOpen() {
        UUID organizationId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        String month = "2026-04";
        Instant attestationUpdatedAt = Instant.parse("2026-04-21T12:00:00Z");
        UUID taskId = closeControlTaskId("CLOSE_ATTESTATION_FOLLOW_UP", organizationId, month);

        stubBaseInboxDependencies(organizationId, month, attestationUpdatedAt);
        when(notificationRepository.findTopByOrganizationIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                organizationId,
                "close_control_follow_up_escalation",
                taskId.toString()))
                .thenReturn(Optional.of(closeControlEscalation(
                        organizationId,
                        taskId,
                        CloseControlDisposition.WAITING_ON_APPROVER,
                        null)));

        WorkflowInboxSummary inbox = reviewQueueService.inbox(organizationId, currentUserId);

        assertThat(inbox.recommendedActionLabel()).isEqualTo("Push approver follow-through");
        assertThat(inbox.recommendedActionKey()).isEqualTo("PUSH_APPROVER_FOLLOW_THROUGH");
        assertThat(inbox.recommendedActionPath()).isEqualTo("/close?month=2026-04");
        assertThat(inbox.recommendedActionUrgency()).isEqualTo(DashboardActionUrgency.HIGH);
        assertThat(inbox.recommendedActionSeverity()).isEqualTo(CloseFollowUpSeverity.ESCALATED);
        assertThat(inbox.attentionTasks()).singleElement().satisfies(task ->
                assertThat(task.closeControlSeverity()).isEqualTo(CloseFollowUpSeverity.ESCALATED));
    }

    @Test
    void inboxRecommendsScheduledRevisitWhenEscalationWasAcknowledgedForTomorrow() {
        UUID organizationId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        String month = "2026-04";
        Instant attestationUpdatedAt = Instant.parse("2026-04-21T12:00:00Z");
        UUID taskId = closeControlTaskId("CLOSE_ATTESTATION_FOLLOW_UP", organizationId, month);
        LocalDate nextTouchOn = LocalDate.of(2026, 4, 30);

        stubBaseInboxDependencies(organizationId, month, attestationUpdatedAt);
        when(notificationRepository.findTopByOrganizationIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                organizationId,
                "close_control_follow_up_escalation",
                taskId.toString()))
                .thenReturn(Optional.of(closeControlEscalation(
                        organizationId,
                        taskId,
                        CloseControlDisposition.REVISIT_TOMORROW,
                        nextTouchOn)));
        when(auditService.listForOrganizationByEventTypeAndEntity(
                organizationId,
                "CLOSE_CONTROL_ESCALATION_ACKNOWLEDGED",
                taskId.toString()))
                .thenReturn(List.of(new AuditEventSummary(
                        UUID.randomUUID(),
                        organizationId,
                        currentUserId,
                        "CLOSE_CONTROL_ESCALATION_ACKNOWLEDGED",
                        "workflow_task",
                        taskId.toString(),
                        "Owner reviewed the escalation and queued the next touch for tomorrow.",
                        attestationUpdatedAt.plusSeconds(3600))));

        WorkflowInboxSummary inbox = reviewQueueService.inbox(organizationId, currentUserId);

        assertThat(inbox.recommendedActionLabel()).isEqualTo("Revisit attestation on Apr 30");
        assertThat(inbox.recommendedActionKey()).isEqualTo("QUEUE_TOMORROWS_CLOSE_FOLLOW_UP");
        assertThat(inbox.recommendedActionPath()).isEqualTo("/close?month=2026-04");
        assertThat(inbox.recommendedActionUrgency()).isEqualTo(DashboardActionUrgency.NORMAL);
        assertThat(inbox.recommendedActionSeverity()).isEqualTo(CloseFollowUpSeverity.SCHEDULED);
        assertThat(inbox.attentionTasks()).singleElement().satisfies(task -> {
            assertThat(task.closeControlSeverity()).isEqualTo(CloseFollowUpSeverity.SCHEDULED);
            assertThat(task.dueDate()).isEqualTo(nextTouchOn);
            assertThat(task.title()).isEqualTo("Revisit close follow-up on 2026-04-30 for 2026-04");
        });
    }

    private void stubBaseInboxDependencies(UUID organizationId, String month, Instant attestationUpdatedAt) {
        when(organizationService.get(organizationId)).thenReturn(null);
        when(taskRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(organizationId, WorkflowTaskStatus.OPEN))
                .thenReturn(List.of());
        when(accountingPeriodRepository.findPeriodContaining(organizationId, LocalDate.parse(month + "-01")))
                .thenReturn(Optional.empty());
        when(auditService.listRecentForOrganizationByEventType(organizationId, "PERIOD_CLOSE_ATTESTATION_UPDATED", 5))
                .thenReturn(List.of(new AuditEventSummary(
                        UUID.randomUUID(),
                        organizationId,
                        UUID.randomUUID(),
                        "PERIOD_CLOSE_ATTESTATION_UPDATED",
                        "accounting_period",
                        month,
                        "Attestation routing updated",
                        attestationUpdatedAt)));
        when(auditService.listRecentForOrganizationByEventType(organizationId, "PERIOD_FORCE_CLOSED", 5))
                .thenReturn(List.of());
        when(auditService.listForOrganizationByEventTypeAndEntity(eq(organizationId), anyString(), eq(month)))
                .thenReturn(List.of());
        when(auditService.listForOrganizationByEventTypeAndEntity(
                organizationId,
                "CLOSE_CONTROL_TASK_ACKNOWLEDGED",
                closeControlTaskId("CLOSE_ATTESTATION_FOLLOW_UP", organizationId, month).toString()))
                .thenReturn(List.of());
        when(auditService.listForOrganizationByEventTypeAndEntity(
                organizationId,
                "CLOSE_CONTROL_TASK_RESOLVED",
                closeControlTaskId("CLOSE_ATTESTATION_FOLLOW_UP", organizationId, month).toString()))
                .thenReturn(List.of());
        when(auditService.listForOrganizationByEventTypeAndEntity(
                organizationId,
                "CLOSE_CONTROL_ESCALATION_ACKNOWLEDGED",
                closeControlTaskId("CLOSE_ATTESTATION_FOLLOW_UP", organizationId, month).toString()))
                .thenReturn(List.of());
    }

    private UUID closeControlTaskId(String taskType, UUID organizationId, String month) {
        return UUID.nameUUIDFromBytes((taskType + ":" + organizationId + ":" + month).getBytes(StandardCharsets.UTF_8));
    }

    private Notification closeControlEscalation(UUID organizationId,
                                                UUID taskId,
                                                CloseControlDisposition disposition,
                                                LocalDate nextTouchOn) {
        Notification notification = new Notification();
        notification.setReferenceType("close_control_follow_up_escalation");
        notification.setReferenceId(taskId.toString());
        notification.setCloseControlDisposition(disposition);
        notification.setCloseControlNextTouchOn(nextTouchOn);
        setField(notification, "createdAt", Instant.parse("2026-04-21T13:00:00Z"));
        return notification;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to set field " + fieldName, ex);
        }
    }
}
