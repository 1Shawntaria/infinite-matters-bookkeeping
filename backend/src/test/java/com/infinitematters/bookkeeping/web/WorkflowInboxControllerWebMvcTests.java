package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.dashboard.DashboardActionUrgency;
import com.infinitematters.bookkeeping.domain.Category;
import com.infinitematters.bookkeeping.notifications.CloseControlDisposition;
import com.infinitematters.bookkeeping.notifications.CloseControlEscalationService;
import com.infinitematters.bookkeeping.notifications.DeadLetterResolutionReasonCode;
import com.infinitematters.bookkeeping.notifications.DeadLetterResolutionStatus;
import com.infinitematters.bookkeeping.notifications.DeadLetterEscalationRunResult;
import com.infinitematters.bookkeeping.notifications.DeadLetterQueueItem;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceMonitorRunResult;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportEffectivenessBucket;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportEffectivenessSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceTaskFilter;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceTaskQueueSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportTaskOperationsSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportTaskSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterTaskRunResult;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportEscalationService;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceMonitorService;
import com.infinitematters.bookkeeping.notifications.DeadLetterWorkflowTaskService;
import com.infinitematters.bookkeeping.notifications.NotificationCategory;
import com.infinitematters.bookkeeping.notifications.NotificationChannel;
import com.infinitematters.bookkeeping.notifications.NotificationDeliveryState;
import com.infinitematters.bookkeeping.notifications.NotificationOperationsSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterQueueSummary;
import com.infinitematters.bookkeeping.notifications.DeadLetterRecommendedAction;
import com.infinitematters.bookkeeping.notifications.NotificationService;
import com.infinitematters.bookkeeping.notifications.CloseControlEscalationRunResult;
import com.infinitematters.bookkeeping.notifications.NotificationRequeueResult;
import com.infinitematters.bookkeeping.notifications.NotificationStatus;
import com.infinitematters.bookkeeping.notifications.NotificationSummary;
import com.infinitematters.bookkeeping.notifications.NotificationSuppressionSummary;
import com.infinitematters.bookkeeping.notifications.NotificationSuppressionService;
import com.infinitematters.bookkeeping.notifications.ReminderRunResult;
import com.infinitematters.bookkeeping.security.BearerTokenAuthenticationFilter;
import com.infinitematters.bookkeeping.security.CsrfProtectionFilter;
import com.infinitematters.bookkeeping.security.RequestIdentityFilter;
import com.infinitematters.bookkeeping.security.RequestLoggingFilter;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.workflows.CloseFollowUpSeverity;
import com.infinitematters.bookkeeping.workflows.ReviewQueueService;
import com.infinitematters.bookkeeping.workflows.ReviewTaskSummary;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import com.infinitematters.bookkeeping.workflows.WorkflowInboxSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WorkflowInboxController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        },
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                        BearerTokenAuthenticationFilter.class,
                        CsrfProtectionFilter.class,
                        RequestIdentityFilter.class,
                        RequestLoggingFilter.class
                })
        })
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class WorkflowInboxControllerWebMvcTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewQueueService reviewQueueService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private DeadLetterWorkflowTaskService deadLetterWorkflowTaskService;

    @MockitoBean
    private DeadLetterSupportPerformanceMonitorService deadLetterSupportPerformanceMonitorService;

    @MockitoBean
    private DeadLetterSupportEscalationService deadLetterSupportEscalationService;

    @MockitoBean
    private CloseControlEscalationService closeControlEscalationService;

    @MockitoBean
    private NotificationSuppressionService suppressionService;

    @MockitoBean
    private TenantAccessService tenantAccessService;

    @Test
    void inboxMapsRecommendationAndTaskSeverityIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        WorkflowInboxSummary inbox = new WorkflowInboxSummary(
                "workflow-inbox",
                1,
                0,
                0,
                1,
                0,
                1,
                "Resume override review on Apr 23",
                "review_override_follow_up",
                "/exceptions?filter=requires_override_follow_up",
                DashboardActionUrgency.HIGH,
                CloseFollowUpSeverity.SCHEDULED,
                List.of(new ReviewTaskSummary(
                        taskId,
                        null,
                        null,
                        "CLOSE_CONTROL_FOLLOW_UP",
                        "HIGH",
                        false,
                        "Resume override review",
                        "Owner acknowledged the escalation and asked to revisit tomorrow.",
                        LocalDate.of(2026, 4, 23),
                        userId,
                        "Owner Example",
                        "Acme Software",
                        BigDecimal.valueOf(131.56),
                        LocalDate.of(2026, 4, 20),
                        Category.SOFTWARE,
                        0.99,
                        "exceptions",
                        "/exceptions?filter=requires_override_follow_up",
                        "Revisit tomorrow after owner acknowledgement.",
                        userId,
                        null,
                        LocalDate.of(2026, 4, 23),
                        null,
                        null,
                        CloseFollowUpSeverity.SCHEDULED)));

        when(tenantAccessService.requireAccess(organizationId)).thenReturn(userId);
        when(reviewQueueService.inbox(organizationId, userId)).thenReturn(inbox);

        mockMvc.perform(get("/api/workflows/inbox")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedActionLabel").value("Resume override review on Apr 23"))
                .andExpect(jsonPath("$.recommendedActionKey").value("review_override_follow_up"))
                .andExpect(jsonPath("$.recommendedActionPath").value("/exceptions?filter=requires_override_follow_up"))
                .andExpect(jsonPath("$.recommendedActionSeverity").value("SCHEDULED"))
                .andExpect(jsonPath("$.attentionTasks[0].taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.attentionTasks[0].title").value("Resume override review"))
                .andExpect(jsonPath("$.attentionTasks[0].closeControlSeverity").value("SCHEDULED"))
                .andExpect(jsonPath("$.attentionTasks[0].snoozedUntil").value("2026-04-23"));

        verify(tenantAccessService).requireAccess(organizationId);
        verify(reviewQueueService).inbox(organizationId, userId);
    }

    @Test
    void acknowledgeInboxAttentionTaskForwardsNoteAndMapsSummary() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReviewTaskSummary summary = new ReviewTaskSummary(
                taskId,
                null,
                null,
                "CLOSE_CONTROL_FOLLOW_UP",
                "HIGH",
                false,
                "Resume override review",
                "Owner asked to revisit tomorrow.",
                LocalDate.of(2026, 4, 23),
                actorUserId,
                "Owner Example",
                "Acme Software",
                BigDecimal.valueOf(131.56),
                LocalDate.of(2026, 4, 20),
                Category.SOFTWARE,
                0.99,
                "exceptions",
                "/exceptions?filter=requires_override_follow_up",
                "Revisit tomorrow after owner acknowledgement.",
                actorUserId,
                Instant.parse("2026-04-22T16:00:00Z"),
                LocalDate.of(2026, 4, 23),
                null,
                null,
                CloseFollowUpSeverity.SCHEDULED);

        when(tenantAccessService.requireAccess(organizationId)).thenReturn(actorUserId);
        when(reviewQueueService.acknowledgeCloseControlTask(
                organizationId,
                taskId,
                actorUserId,
                "Revisit tomorrow after owner acknowledgement.")).thenReturn(summary);

        mockMvc.perform(post("/api/workflows/inbox/attention-tasks/" + taskId + "/acknowledge")
                        .param("organizationId", organizationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note":"Revisit tomorrow after owner acknowledgement."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.resolutionComment").value("Revisit tomorrow after owner acknowledgement."))
                .andExpect(jsonPath("$.acknowledgedByUserId").value(actorUserId.toString()))
                .andExpect(jsonPath("$.closeControlSeverity").value("SCHEDULED"));

        verify(tenantAccessService).requireAccess(organizationId);
        verify(reviewQueueService).acknowledgeCloseControlTask(
                organizationId,
                taskId,
                actorUserId,
                "Revisit tomorrow after owner acknowledgement.");
    }

    @Test
    void resolveInboxAttentionTaskForwardsNoteToRoleProtectedAction() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);

        mockMvc.perform(post("/api/workflows/inbox/attention-tasks/" + taskId + "/resolve")
                        .param("organizationId", organizationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note":"Resolved after documenting the override disposition."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").doesNotExist());

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(reviewQueueService).resolveCloseControlTask(
                organizationId,
                taskId,
                actorUserId,
                "Resolved after documenting the override disposition.");
    }

    @Test
    void notificationsMapsNotificationListIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationSummary summary = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.PENDING,
                NotificationDeliveryState.PENDING,
                "Reminder queued for an open workflow task.",
                "WORKFLOW_TASK",
                "task-123",
                "owner@acme.test",
                "sendgrid",
                "provider-123",
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-23T09:00:00Z"),
                null,
                null,
                Instant.parse("2026-04-22T14:55:00Z"));

        when(tenantAccessService.requireAccess(organizationId)).thenReturn(actorUserId);
        when(notificationService.listForOrganization(organizationId)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/workflows/notifications")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].deliveryState").value("PENDING"))
                .andExpect(jsonPath("$[0].recipientEmail").value("owner@acme.test"));

        verify(tenantAccessService).requireAccess(organizationId);
        verify(notificationService).listForOrganization(organizationId);
    }

    @Test
    void attentionNotificationsMapOperationsSummaryListIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationSummary attention = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.FAILED,
                NotificationDeliveryState.FAILED,
                "Delivery failed and needs operational review.",
                "WORKFLOW_TASK",
                "task-123",
                "ops@acme.test",
                "sendgrid",
                "provider-123",
                2,
                "Mailbox unavailable",
                "550",
                DeadLetterResolutionStatus.OPEN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-23T10:00:00Z"),
                Instant.parse("2026-04-22T15:05:00Z"),
                null,
                Instant.parse("2026-04-22T14:55:00Z"));
        NotificationOperationsSummary operationsSummary = new NotificationOperationsSummary(
                1,
                1,
                0,
                1,
                0,
                0,
                List.of(attention),
                null);

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(notificationService.operationsSummary(organizationId)).thenReturn(operationsSummary);

        mockMvc.perform(get("/api/workflows/notifications/attention")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$[0].deliveryState").value("FAILED"))
                .andExpect(jsonPath("$[0].deadLetterResolutionStatus").value("OPEN"))
                .andExpect(jsonPath("$[0].lastFailureCode").value("550"));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(notificationService).operationsSummary(organizationId);
    }

    @Test
    void deadLetterNotificationsMapOpenDeadLetterListIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationSummary summary = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.FAILED,
                NotificationDeliveryState.FAILED,
                "Delivery failed for workflow reminder.",
                "WORKFLOW_TASK",
                "task-123",
                "ops@acme.test",
                "sendgrid",
                "provider-123",
                2,
                "Mailbox unavailable",
                "550",
                DeadLetterResolutionStatus.OPEN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-23T10:00:00Z"),
                Instant.parse("2026-04-22T15:05:00Z"),
                null,
                Instant.parse("2026-04-22T14:55:00Z"));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(notificationService.deadLetters(organizationId)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/workflows/notifications/dead-letter")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$[0].deliveryState").value("FAILED"))
                .andExpect(jsonPath("$[0].deadLetterResolutionStatus").value("OPEN"))
                .andExpect(jsonPath("$[0].recipientEmail").value("ops@acme.test"));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(notificationService).deadLetters(organizationId);
    }

    @Test
    void acknowledgeCloseControlEscalationForwardsDispositionAndNextTouchOn() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        LocalDate nextTouchOn = LocalDate.of(2026, 4, 23);
        NotificationSummary summary = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.SENT,
                NotificationDeliveryState.DELIVERED,
                "Force-close review needs owner follow-up.",
                "ACCOUNTING_PERIOD",
                "2026-04",
                "owner@acme.test",
                "sendgrid",
                "provider-123",
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Revisit tomorrow after owner acknowledgement.",
                Instant.parse("2026-04-22T16:30:00Z"),
                actorUserId,
                CloseControlDisposition.REVISIT_TOMORROW,
                nextTouchOn,
                null,
                null,
                null,
                CloseFollowUpSeverity.SCHEDULED,
                Instant.parse("2026-04-22T15:00:00Z"),
                Instant.parse("2026-04-22T15:05:00Z"),
                Instant.parse("2026-04-22T15:06:00Z"),
                Instant.parse("2026-04-22T14:55:00Z"));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(notificationService.acknowledgeCloseControlEscalation(
                organizationId,
                notificationId,
                actorUserId,
                "Revisit tomorrow after owner acknowledgement.",
                CloseControlDisposition.REVISIT_TOMORROW,
                nextTouchOn)).thenReturn(summary);

        mockMvc.perform(post("/api/workflows/notifications/" + notificationId + "/close-control-escalation/acknowledge")
                        .param("organizationId", organizationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note":"Revisit tomorrow after owner acknowledgement.",
                                  "disposition":"REVISIT_TOMORROW",
                                  "nextTouchOn":"2026-04-23"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()))
                .andExpect(jsonPath("$.closeControlAcknowledgementNote").value("Revisit tomorrow after owner acknowledgement."))
                .andExpect(jsonPath("$.closeControlDisposition").value("REVISIT_TOMORROW"))
                .andExpect(jsonPath("$.closeControlNextTouchOn").value("2026-04-23"))
                .andExpect(jsonPath("$.closeControlSeverity").value("SCHEDULED"));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(notificationService).acknowledgeCloseControlEscalation(
                organizationId,
                notificationId,
                actorUserId,
                "Revisit tomorrow after owner acknowledgement.",
                CloseControlDisposition.REVISIT_TOMORROW,
                nextTouchOn);
    }

    @Test
    void resolveCloseControlEscalationMapsResolutionFieldsIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationSummary summary = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.SENT,
                NotificationDeliveryState.DELIVERED,
                "Force-close review needs owner follow-up.",
                "ACCOUNTING_PERIOD",
                "2026-04",
                "owner@acme.test",
                "sendgrid",
                "provider-123",
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                CloseControlDisposition.OVERRIDE_DOCS_IN_PROGRESS,
                null,
                "Escalation resolved after documenting the owner disposition.",
                Instant.parse("2026-04-22T18:00:00Z"),
                actorUserId,
                CloseFollowUpSeverity.ROUTINE,
                Instant.parse("2026-04-22T15:00:00Z"),
                Instant.parse("2026-04-22T15:05:00Z"),
                Instant.parse("2026-04-22T15:06:00Z"),
                Instant.parse("2026-04-22T14:55:00Z"));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(notificationService.resolveCloseControlEscalation(
                organizationId,
                notificationId,
                actorUserId,
                "Escalation resolved after documenting the owner disposition.",
                CloseControlDisposition.OVERRIDE_DOCS_IN_PROGRESS,
                null)).thenReturn(summary);

        mockMvc.perform(post("/api/workflows/notifications/" + notificationId + "/close-control-escalation/resolve")
                        .param("organizationId", organizationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note":"Escalation resolved after documenting the owner disposition.",
                                  "disposition":"OVERRIDE_DOCS_IN_PROGRESS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()))
                .andExpect(jsonPath("$.closeControlDisposition").value("OVERRIDE_DOCS_IN_PROGRESS"))
                .andExpect(jsonPath("$.closeControlResolutionNote").value("Escalation resolved after documenting the owner disposition."))
                .andExpect(jsonPath("$.closeControlResolvedByUserId").value(actorUserId.toString()))
                .andExpect(jsonPath("$.closeControlSeverity").value("ROUTINE"));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(notificationService).resolveCloseControlEscalation(
                organizationId,
                notificationId,
                actorUserId,
                "Escalation resolved after documenting the owner disposition.",
                CloseControlDisposition.OVERRIDE_DOCS_IN_PROGRESS,
                null);
    }

    @Test
    void acknowledgeDeadLetterForwardsNoteAndMapsResolutionFields() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationSummary summary = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.FAILED,
                NotificationDeliveryState.FAILED,
                "Delivery failed for workflow reminder.",
                "WORKFLOW_TASK",
                "task-123",
                "ops@acme.test",
                "sendgrid",
                "provider-123",
                2,
                "Mailbox unavailable",
                "550",
                DeadLetterResolutionStatus.ACKNOWLEDGED,
                null,
                "Ops is already investigating this mailbox issue.",
                Instant.parse("2026-04-22T17:15:00Z"),
                actorUserId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-23T10:00:00Z"),
                Instant.parse("2026-04-22T15:05:00Z"),
                null,
                Instant.parse("2026-04-22T14:55:00Z"));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(notificationService.acknowledgeDeadLetter(
                organizationId,
                notificationId,
                actorUserId,
                "Ops is already investigating this mailbox issue.")).thenReturn(summary);

        mockMvc.perform(post("/api/workflows/notifications/" + notificationId + "/dead-letter/acknowledge")
                        .param("organizationId", organizationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note":"Ops is already investigating this mailbox issue."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()))
                .andExpect(jsonPath("$.deadLetterResolutionStatus").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.deadLetterResolutionNote").value("Ops is already investigating this mailbox issue."))
                .andExpect(jsonPath("$.deadLetterResolvedByUserId").value(actorUserId.toString()));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(notificationService).acknowledgeDeadLetter(
                organizationId,
                notificationId,
                actorUserId,
                "Ops is already investigating this mailbox issue.");
    }

    @Test
    void resolveDeadLetterForwardsNoteAndMapsResolutionFields() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationSummary summary = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.FAILED,
                NotificationDeliveryState.FAILED,
                "Delivery failed for workflow reminder.",
                "WORKFLOW_TASK",
                "task-123",
                "ops@acme.test",
                "sendgrid",
                "provider-123",
                2,
                "Mailbox unavailable",
                "550",
                DeadLetterResolutionStatus.RESOLVED,
                DeadLetterResolutionReasonCode.OTHER,
                "Resolved after confirming no further action is needed.",
                Instant.parse("2026-04-22T18:00:00Z"),
                actorUserId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-23T10:00:00Z"),
                Instant.parse("2026-04-22T15:05:00Z"),
                null,
                Instant.parse("2026-04-22T14:55:00Z"));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(notificationService.resolveDeadLetter(
                organizationId,
                notificationId,
                actorUserId,
                "Resolved after confirming no further action is needed.")).thenReturn(summary);

        mockMvc.perform(post("/api/workflows/notifications/" + notificationId + "/dead-letter/resolve")
                        .param("organizationId", organizationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note":"Resolved after confirming no further action is needed."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()))
                .andExpect(jsonPath("$.deadLetterResolutionStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.deadLetterResolutionReasonCode").value("OTHER"))
                .andExpect(jsonPath("$.deadLetterResolutionNote").value("Resolved after confirming no further action is needed."))
                .andExpect(jsonPath("$.deadLetterResolvedByUserId").value(actorUserId.toString()));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(notificationService).resolveDeadLetter(
                organizationId,
                notificationId,
                actorUserId,
                "Resolved after confirming no further action is needed.");
    }

    @Test
    void retryDeadLetterForwardsRecipientOverrideAndNote() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationSummary summary = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.PENDING,
                NotificationDeliveryState.PENDING,
                "Retry this dead-letter notification with the corrected destination.",
                "WORKFLOW_TASK",
                "task-123",
                "ops-updated@acme.test",
                "sendgrid",
                "provider-123",
                2,
                null,
                null,
                DeadLetterResolutionStatus.OPEN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-22T15:00:00Z"),
                null,
                null,
                Instant.parse("2026-04-22T14:55:00Z"));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(notificationService.retryDeadLetter(
                organizationId,
                notificationId,
                actorUserId,
                "ops-updated@acme.test",
                "Retry with the corrected mailbox.")).thenReturn(summary);

        mockMvc.perform(post("/api/workflows/notifications/" + notificationId + "/dead-letter/retry")
                        .param("organizationId", organizationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientEmail":"ops-updated@acme.test",
                                  "note":"Retry with the corrected mailbox."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()))
                .andExpect(jsonPath("$.recipientEmail").value("ops-updated@acme.test"))
                .andExpect(jsonPath("$.deliveryState").value("PENDING"))
                .andExpect(jsonPath("$.deadLetterResolutionStatus").value("OPEN"));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(notificationService).retryDeadLetter(
                organizationId,
                notificationId,
                actorUserId,
                "ops-updated@acme.test",
                "Retry with the corrected mailbox.");
    }

    @Test
    void requeueSingleNotificationMapsRecoveryFieldsIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationSummary summary = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.PENDING,
                NotificationDeliveryState.PENDING,
                "Queued again after dead-letter review.",
                "WORKFLOW_TASK",
                "task-123",
                "owner@acme.test",
                "sendgrid",
                "provider-123",
                3,
                null,
                null,
                DeadLetterResolutionStatus.OPEN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-22T18:00:00Z"),
                null,
                null,
                Instant.parse("2026-04-22T14:55:00Z"));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(notificationService.requeueFailedNotification(organizationId, notificationId)).thenReturn(summary);

        mockMvc.perform(post("/api/workflows/notifications/" + notificationId + "/requeue")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.deliveryState").value("PENDING"))
                .andExpect(jsonPath("$.deadLetterResolutionStatus").value("OPEN"));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(notificationService).requeueFailedNotification(organizationId, notificationId);
    }

    @Test
    void resolveDeadLetterNoResendForwardsReasonCodeAndNote() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationSummary summary = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.SENT,
                NotificationDeliveryState.FAILED,
                "Delivery no longer required after external correction.",
                "WORKFLOW_TASK",
                "task-123",
                "ops@acme.test",
                "sendgrid",
                "provider-123",
                2,
                "Mailbox unavailable",
                "550",
                DeadLetterResolutionStatus.RESOLVED,
                DeadLetterResolutionReasonCode.DELIVERY_NO_LONGER_REQUIRED,
                "Handled externally; no resend needed.",
                Instant.parse("2026-04-22T17:00:00Z"),
                actorUserId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-22T15:00:00Z"),
                Instant.parse("2026-04-22T15:05:00Z"),
                null,
                Instant.parse("2026-04-22T14:55:00Z"));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(notificationService.resolveDeadLetterNoResend(
                organizationId,
                notificationId,
                actorUserId,
                DeadLetterResolutionReasonCode.DELIVERY_NO_LONGER_REQUIRED,
                "Handled externally; no resend needed.")).thenReturn(summary);

        mockMvc.perform(post("/api/workflows/notifications/" + notificationId + "/dead-letter/no-resend")
                        .param("organizationId", organizationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reasonCode":"DELIVERY_NO_LONGER_REQUIRED",
                                  "note":"Handled externally; no resend needed."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()))
                .andExpect(jsonPath("$.deadLetterResolutionStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.deadLetterResolutionReasonCode").value("DELIVERY_NO_LONGER_REQUIRED"))
                .andExpect(jsonPath("$.deadLetterResolutionNote").value("Handled externally; no resend needed."));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(notificationService).resolveDeadLetterNoResend(
                organizationId,
                notificationId,
                actorUserId,
                DeadLetterResolutionReasonCode.DELIVERY_NO_LONGER_REQUIRED,
                "Handled externally; no resend needed.");
    }

    @Test
    void suppressionsMapsActiveSuppressionResponseIntoBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID suppressionId = UUID.randomUUID();
        UUID sourceNotificationId = UUID.randomUUID();
        NotificationSuppressionSummary summary = new NotificationSuppressionSummary(
                suppressionId,
                "suppressed@example.test",
                "sendgrid",
                "BOUNCED",
                sourceNotificationId,
                Instant.parse("2026-04-22T17:00:00Z"),
                Instant.parse("2026-04-22T16:30:00Z"));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(UUID.randomUUID());
        when(suppressionService.listActiveSuppressions(organizationId)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/workflows/notifications/suppressions")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].suppressionId").value(suppressionId.toString()))
                .andExpect(jsonPath("$[0].email").value("suppressed@example.test"))
                .andExpect(jsonPath("$[0].providerName").value("sendgrid"))
                .andExpect(jsonPath("$[0].reason").value("BOUNCED"))
                .andExpect(jsonPath("$[0].sourceNotificationId").value(sourceNotificationId.toString()));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(suppressionService).listActiveSuppressions(organizationId);
    }

    @Test
    void deactivateSuppressionMapsUpdatedSuppressionIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID suppressionId = UUID.randomUUID();
        UUID sourceNotificationId = UUID.randomUUID();
        NotificationSuppressionSummary summary = new NotificationSuppressionSummary(
                suppressionId,
                "suppressed@example.test",
                "sendgrid",
                "BOUNCED",
                sourceNotificationId,
                Instant.parse("2026-04-22T17:00:00Z"),
                Instant.parse("2026-04-22T16:30:00Z"));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(suppressionService.deactivate(organizationId, suppressionId)).thenReturn(summary);

        mockMvc.perform(post("/api/workflows/notifications/suppressions/" + suppressionId + "/deactivate")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suppressionId").value(suppressionId.toString()))
                .andExpect(jsonPath("$.email").value("suppressed@example.test"))
                .andExpect(jsonPath("$.providerName").value("sendgrid"))
                .andExpect(jsonPath("$.sourceNotificationId").value(sourceNotificationId.toString()));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(suppressionService).deactivate(organizationId, suppressionId);
    }

    @Test
    void requeueFailedNotificationsMapsRecoverySummaryIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationSummary notification = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.PENDING,
                NotificationDeliveryState.PENDING,
                "Queued again after dead-letter review.",
                "WORKFLOW_TASK",
                "task-123",
                "owner@acme.test",
                "sendgrid",
                "provider-123",
                3,
                null,
                null,
                DeadLetterResolutionStatus.OPEN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-22T18:00:00Z"),
                null,
                null,
                Instant.parse("2026-04-22T14:55:00Z"));
        NotificationRequeueResult result = new NotificationRequeueResult(1, List.of(notification));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(notificationService.requeueFailedNotifications(organizationId)).thenReturn(result);

        mockMvc.perform(post("/api/workflows/notifications/requeue-failed")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requeuedCount").value(1))
                .andExpect(jsonPath("$.notifications[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$.notifications[0].status").value("PENDING"))
                .andExpect(jsonPath("$.notifications[0].deliveryState").value("PENDING"));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(notificationService).requeueFailedNotifications(organizationId);
    }

    @Test
    void deadLetterSupportPerformanceTasksForwardsFilterAndMapsTaskSummary() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        WorkflowTask task = new WorkflowTask();
        ReviewTaskSummary summary = new ReviewTaskSummary(
                taskId,
                null,
                null,
                "DEAD_LETTER_SUPPORT_PERFORMANCE",
                "CRITICAL",
                true,
                "Acknowledged performance risk",
                "Owner is already working this",
                LocalDate.of(2026, 4, 22),
                actorUserId,
                "Owner Example",
                null,
                null,
                null,
                null,
                0.0,
                "workflows",
                "/workflows/notifications/dead-letter/performance/tasks",
                null,
                actorUserId,
                Instant.parse("2026-04-22T16:00:00Z"),
                null,
                null,
                null,
                null);

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(deadLetterSupportPerformanceMonitorService.listOpenRiskTasks(
                organizationId,
                DeadLetterSupportPerformanceTaskFilter.ACKNOWLEDGED)).thenReturn(List.of(task));
        when(reviewQueueService.toSummary(task)).thenReturn(summary);

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/performance/tasks")
                        .param("organizationId", organizationId.toString())
                        .param("filter", "ACKNOWLEDGED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value(taskId.toString()))
                .andExpect(jsonPath("$[0].taskType").value("DEAD_LETTER_SUPPORT_PERFORMANCE"))
                .andExpect(jsonPath("$[0].title").value("Acknowledged performance risk"))
                .andExpect(jsonPath("$[0].acknowledgedByUserId").value(actorUserId.toString()));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(deadLetterSupportPerformanceMonitorService).listOpenRiskTasks(
                organizationId,
                DeadLetterSupportPerformanceTaskFilter.ACKNOWLEDGED);
        verify(reviewQueueService).toSummary(task);
    }

    @Test
    void highPriorityDeadLetterSupportPerformanceTasksMapReviewTaskSummaries() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        WorkflowTask task = new WorkflowTask();
        ReviewTaskSummary summary = new ReviewTaskSummary(
                taskId,
                null,
                null,
                "DEAD_LETTER_SUPPORT_PERFORMANCE",
                "CRITICAL",
                true,
                "Unacknowledged dead-letter performance risk",
                "Retry backlog keeps growing.",
                LocalDate.of(2026, 4, 22),
                null,
                null,
                null,
                null,
                null,
                null,
                0.0,
                "workflows",
                "/workflows/notifications/dead-letter/performance/tasks",
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(deadLetterSupportPerformanceMonitorService.listHighPriorityRiskTasks(organizationId)).thenReturn(List.of(task));
        when(reviewQueueService.toSummary(task)).thenReturn(summary);

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/performance/tasks/high-priority")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value(taskId.toString()))
                .andExpect(jsonPath("$[0].taskType").value("DEAD_LETTER_SUPPORT_PERFORMANCE"))
                .andExpect(jsonPath("$[0].title").value("Unacknowledged dead-letter performance risk"))
                .andExpect(jsonPath("$[0].actionPath").value("/workflows/notifications/dead-letter/performance/tasks"));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(deadLetterSupportPerformanceMonitorService).listHighPriorityRiskTasks(organizationId);
        verify(reviewQueueService).toSummary(task);
    }

    @Test
    void deadLetterSupportPerformanceSummaryMapsQueueCountsIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        DeadLetterSupportPerformanceTaskQueueSummary summary = new DeadLetterSupportPerformanceTaskQueueSummary(
                2,
                1,
                1,
                1,
                1,
                0,
                1,
                0,
                1,
                0,
                0);

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(deadLetterSupportPerformanceMonitorService.queueSummary(organizationId)).thenReturn(summary);

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/performance/summary")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openTaskCount").value(2))
                .andExpect(jsonPath("$.assignedTaskCount").value(1))
                .andExpect(jsonPath("$.unassignedTaskCount").value(1))
                .andExpect(jsonPath("$.acknowledgedTaskCount").value(1))
                .andExpect(jsonPath("$.unacknowledgedTaskCount").value(1))
                .andExpect(jsonPath("$.reactivatedNeedsAttentionCount").value(1));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(deadLetterSupportPerformanceMonitorService).queueSummary(organizationId);
    }

    @Test
    void acknowledgeDeadLetterSupportPerformanceTaskForwardsNoteAndMapsSummary() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        WorkflowTask task = new WorkflowTask();
        ReviewTaskSummary summary = new ReviewTaskSummary(
                taskId,
                null,
                null,
                "DEAD_LETTER_SUPPORT_PERFORMANCE",
                "CRITICAL",
                true,
                "Acknowledged dead-letter performance risk",
                "Ops is already addressing the spike.",
                LocalDate.of(2026, 4, 22),
                actorUserId,
                "Owner Example",
                null,
                null,
                null,
                null,
                0.0,
                "workflows",
                "/workflows/notifications/dead-letter/performance/tasks",
                "Monitoring delivery backlog while ops investigates.",
                actorUserId,
                Instant.parse("2026-04-22T16:00:00Z"),
                null,
                null,
                null,
                null);

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(deadLetterSupportPerformanceMonitorService.acknowledgeRiskTask(
                organizationId,
                taskId,
                actorUserId,
                "Monitoring delivery backlog while ops investigates.")).thenReturn(task);
        when(reviewQueueService.toSummary(task)).thenReturn(summary);

        mockMvc.perform(post("/api/workflows/notifications/dead-letter/performance/tasks/" + taskId + "/acknowledge")
                        .param("organizationId", organizationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note":"Monitoring delivery backlog while ops investigates."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.resolutionComment").value("Monitoring delivery backlog while ops investigates."))
                .andExpect(jsonPath("$.acknowledgedByUserId").value(actorUserId.toString()));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(deadLetterSupportPerformanceMonitorService).acknowledgeRiskTask(
                organizationId,
                taskId,
                actorUserId,
                "Monitoring delivery backlog while ops investigates.");
        verify(reviewQueueService).toSummary(task);
    }

    @Test
    void snoozeDeadLetterSupportPerformanceTaskForwardsDateAndNoteAndMapsSummary() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        LocalDate snoozedUntil = LocalDate.of(2026, 4, 25);
        WorkflowTask task = new WorkflowTask();
        ReviewTaskSummary summary = new ReviewTaskSummary(
                taskId,
                null,
                null,
                "DEAD_LETTER_SUPPORT_PERFORMANCE",
                "HIGH",
                false,
                "Snoozed dead-letter performance risk",
                "Paused until the provider window closes.",
                snoozedUntil,
                actorUserId,
                "Owner Example",
                null,
                null,
                null,
                null,
                0.0,
                "workflows",
                "/workflows/notifications/dead-letter/performance/tasks",
                "Wait for the provider maintenance window to finish.",
                actorUserId,
                Instant.parse("2026-04-22T16:15:00Z"),
                snoozedUntil,
                null,
                null,
                null);

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(deadLetterSupportPerformanceMonitorService.snoozeRiskTask(
                organizationId,
                taskId,
                actorUserId,
                snoozedUntil,
                "Wait for the provider maintenance window to finish.")).thenReturn(task);
        when(reviewQueueService.toSummary(task)).thenReturn(summary);

        mockMvc.perform(post("/api/workflows/notifications/dead-letter/performance/tasks/" + taskId + "/snooze")
                        .param("organizationId", organizationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "snoozedUntil":"2026-04-25",
                                  "note":"Wait for the provider maintenance window to finish."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.acknowledgedByUserId").value(actorUserId.toString()))
                .andExpect(jsonPath("$.snoozedUntil").value("2026-04-25"))
                .andExpect(jsonPath("$.resolutionComment").value("Wait for the provider maintenance window to finish."));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(deadLetterSupportPerformanceMonitorService).snoozeRiskTask(
                organizationId,
                taskId,
                actorUserId,
                snoozedUntil,
                "Wait for the provider maintenance window to finish.");
        verify(reviewQueueService).toSummary(task);
    }

    @Test
    void resolveDeadLetterSupportPerformanceTaskForwardsNoteAndMapsSummary() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        WorkflowTask task = new WorkflowTask();
        ReviewTaskSummary summary = new ReviewTaskSummary(
                taskId,
                null,
                null,
                "DEAD_LETTER_SUPPORT_PERFORMANCE",
                "HIGH",
                false,
                "Resolved dead-letter performance risk",
                "Backlog returned to normal after provider recovery.",
                LocalDate.of(2026, 4, 22),
                actorUserId,
                "Owner Example",
                null,
                null,
                null,
                null,
                0.0,
                "workflows",
                "/workflows/notifications/dead-letter/performance/tasks",
                "Resolved after the backlog normalized.",
                actorUserId,
                Instant.parse("2026-04-22T16:30:00Z"),
                null,
                null,
                null,
                null);

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(deadLetterSupportPerformanceMonitorService.resolveRiskTask(
                organizationId,
                taskId,
                actorUserId,
                "Resolved after the backlog normalized.")).thenReturn(task);
        when(reviewQueueService.toSummary(task)).thenReturn(summary);

        mockMvc.perform(post("/api/workflows/notifications/dead-letter/performance/tasks/" + taskId + "/resolve")
                        .param("organizationId", organizationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note":"Resolved after the backlog normalized."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.resolutionComment").value("Resolved after the backlog normalized."))
                .andExpect(jsonPath("$.acknowledgedByUserId").value(actorUserId.toString()));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(deadLetterSupportPerformanceMonitorService).resolveRiskTask(
                organizationId,
                taskId,
                actorUserId,
                "Resolved after the backlog normalized.");
        verify(reviewQueueService).toSummary(task);
    }

    @Test
    void deadLetterSupportTasksMapsOperationsSummaryIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID assignedToUserId = UUID.randomUUID();
        DeadLetterSupportTaskOperationsSummary summary = new DeadLetterSupportTaskOperationsSummary(
                7,
                2,
                3,
                4,
                5,
                1,
                2,
                3,
                List.of(new DeadLetterSupportTaskSummary(
                        taskId,
                        notificationId,
                        DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY,
                        "Recipient is suppressed and needs manual review before retry.",
                        "HIGH",
                        true,
                        true,
                        true,
                        false,
                        false,
                        9,
                        2,
                        Instant.parse("2026-04-22T12:00:00Z"),
                        LocalDate.of(2026, 4, 20),
                        assignedToUserId,
                        "Owner Example",
                        "ops@acme.test",
                        "Suppressed delivery retry",
                        "Bounce handling requires owner follow-up.")));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(deadLetterWorkflowTaskService.operationsSummary(organizationId)).thenReturn(summary);

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/tasks")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openCount").value(7))
                .andExpect(jsonPath("$.unassignedCount").value(2))
                .andExpect(jsonPath("$.overdueCount").value(3))
                .andExpect(jsonPath("$.staleCount").value(4))
                .andExpect(jsonPath("$.escalatedCount").value(5))
                .andExpect(jsonPath("$.ignoredEscalationCount").value(1))
                .andExpect(jsonPath("$.assignedAfterEscalationCount").value(2))
                .andExpect(jsonPath("$.resolvedAfterEscalationCount").value(3))
                .andExpect(jsonPath("$.oldestTasks[0].taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.oldestTasks[0].notificationId").value(notificationId.toString()))
                .andExpect(jsonPath("$.oldestTasks[0].recommendedAction").value("UNSUPPRESS_AND_RETRY"))
                .andExpect(jsonPath("$.oldestTasks[0].recommendationReason").value("Recipient is suppressed and needs manual review before retry."))
                .andExpect(jsonPath("$.oldestTasks[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.oldestTasks[0].overdue").value(true))
                .andExpect(jsonPath("$.oldestTasks[0].stale").value(true))
                .andExpect(jsonPath("$.oldestTasks[0].ignoredEscalation").value(true))
                .andExpect(jsonPath("$.oldestTasks[0].ageDays").value(9))
                .andExpect(jsonPath("$.oldestTasks[0].escalationCount").value(2))
                .andExpect(jsonPath("$.oldestTasks[0].dueDate").value("2026-04-20"))
                .andExpect(jsonPath("$.oldestTasks[0].assignedToUserId").value(assignedToUserId.toString()))
                .andExpect(jsonPath("$.oldestTasks[0].assignedToUserName").value("Owner Example"))
                .andExpect(jsonPath("$.oldestTasks[0].recipientEmail").value("ops@acme.test"))
                .andExpect(jsonPath("$.oldestTasks[0].title").value("Suppressed delivery retry"))
                .andExpect(jsonPath("$.oldestTasks[0].description").value("Bounce handling requires owner follow-up."));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(deadLetterWorkflowTaskService).operationsSummary(organizationId);
    }

    @Test
    void deadLetterSupportEffectivenessForwardsWeeksAndMapsBucketsIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        DeadLetterSupportEffectivenessSummary summary = new DeadLetterSupportEffectivenessSummary(
                LocalDate.of(2026, 3, 9),
                LocalDate.of(2026, 5, 3),
                8,
                6,
                2,
                3,
                4,
                List.of(new DeadLetterSupportEffectivenessBucket(
                        LocalDate.of(2026, 4, 27),
                        LocalDate.of(2026, 5, 3),
                        2,
                        1,
                        1,
                        1)));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(deadLetterWorkflowTaskService.effectivenessSummary(organizationId, 8)).thenReturn(summary);

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/effectiveness")
                        .param("organizationId", organizationId.toString())
                        .param("weeks", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromWeekStart").value("2026-03-09"))
                .andExpect(jsonPath("$.toWeekEnd").value("2026-05-03"))
                .andExpect(jsonPath("$.weeks").value(8))
                .andExpect(jsonPath("$.escalatedCount").value(6))
                .andExpect(jsonPath("$.ignoredEscalationCount").value(2))
                .andExpect(jsonPath("$.assignedAfterEscalationCount").value(3))
                .andExpect(jsonPath("$.resolvedAfterEscalationCount").value(4))
                .andExpect(jsonPath("$.buckets[0].weekStart").value("2026-04-27"))
                .andExpect(jsonPath("$.buckets[0].weekEnd").value("2026-05-03"))
                .andExpect(jsonPath("$.buckets[0].escalatedCount").value(2))
                .andExpect(jsonPath("$.buckets[0].ignoredEscalationCount").value(1))
                .andExpect(jsonPath("$.buckets[0].assignedAfterEscalationCount").value(1))
                .andExpect(jsonPath("$.buckets[0].resolvedAfterEscalationCount").value(1));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(deadLetterWorkflowTaskService).effectivenessSummary(organizationId, 8);
    }

    @Test
    void runRemindersMapsCreatedCountAndNotificationsIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationSummary notification = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.PENDING,
                NotificationDeliveryState.PENDING,
                "Reminder queued for outstanding workflow task.",
                "WORKFLOW_TASK",
                "task-123",
                "owner@acme.test",
                "sendgrid",
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-23T09:00:00Z"),
                null,
                null,
                Instant.parse("2026-04-22T14:55:00Z"));
        ReminderRunResult result = new ReminderRunResult(1, List.of(notification));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(notificationService.generateTaskReminders(organizationId)).thenReturn(result);

        mockMvc.perform(post("/api/workflows/reminders/run")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.notifications[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$.notifications[0].scheduledFor").value("2026-04-23T09:00:00Z"));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(notificationService).generateTaskReminders(organizationId);
    }

    @Test
    void runDeadLetterOperationalEndpointsMapResultCountsIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationSummary notification = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.PENDING,
                NotificationDeliveryState.PENDING,
                "Escalation queued for unresolved dead-letter issue.",
                "WORKFLOW_TASK",
                "task-123",
                "ops@acme.test",
                "sendgrid",
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-23T10:00:00Z"),
                null,
                null,
                Instant.parse("2026-04-22T14:55:00Z"));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(deadLetterSupportPerformanceMonitorService.syncOrganization(organizationId))
                .thenReturn(new DeadLetterSupportPerformanceMonitorRunResult(2, 1, 1));
        when(deadLetterWorkflowTaskService.syncOrganization(organizationId))
                .thenReturn(new DeadLetterTaskRunResult(3, 1));
        when(deadLetterSupportEscalationService.run(organizationId))
                .thenReturn(new DeadLetterEscalationRunResult(1, List.of(notification)));
        when(closeControlEscalationService.run(organizationId))
                .thenReturn(new CloseControlEscalationRunResult(1, List.of(notification)));

        mockMvc.perform(post("/api/workflows/notifications/dead-letter/performance/run")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(2))
                .andExpect(jsonPath("$.closedCount").value(1))
                .andExpect(jsonPath("$.escalatedCount").value(1));

        mockMvc.perform(post("/api/workflows/notifications/dead-letter/tasks/run")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(3))
                .andExpect(jsonPath("$.closedCount").value(1));

        mockMvc.perform(post("/api/workflows/notifications/dead-letter/escalations/run")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.notifications[0].id").value(notificationId.toString()));

        mockMvc.perform(post("/api/workflows/close-control/escalations/run")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.notifications[0].id").value(notificationId.toString()));

        verify(tenantAccessService, times(4)).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(deadLetterSupportPerformanceMonitorService).syncOrganization(organizationId);
        verify(deadLetterWorkflowTaskService).syncOrganization(organizationId);
        verify(deadLetterSupportEscalationService).run(organizationId);
        verify(closeControlEscalationService).run(organizationId);
    }

    @Test
    void deadLetterQueueMapsNestedRecommendationShapeIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID suppressionId = UUID.randomUUID();
        NotificationSummary notification = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.FAILED,
                NotificationDeliveryState.FAILED,
                "Delivery failed for workflow task reminder.",
                "WORKFLOW_TASK",
                "task-123",
                "ops@acme.test",
                "sendgrid",
                null,
                2,
                "Mailbox unavailable",
                "550",
                DeadLetterResolutionStatus.OPEN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-23T10:00:00Z"),
                Instant.parse("2026-04-22T15:05:00Z"),
                null,
                Instant.parse("2026-04-22T14:55:00Z"));
        NotificationSuppressionSummary suppression = new NotificationSuppressionSummary(
                suppressionId,
                "ops@acme.test",
                "sendgrid",
                "BOUNCED",
                notificationId,
                Instant.parse("2026-04-22T16:00:00Z"),
                Instant.parse("2026-04-22T15:30:00Z"));
        DeadLetterQueueSummary queue = new DeadLetterQueueSummary(
                List.of(new DeadLetterQueueItem(
                        notification,
                        DeadLetterRecommendedAction.RETRY_DELIVERY,
                        false,
                        null,
                        "Retry is safe because the recipient is not suppressed.")),
                List.of(new DeadLetterQueueItem(
                        notification,
                        DeadLetterRecommendedAction.UNSUPPRESS_AND_RETRY,
                        true,
                        suppression,
                        "Unsuppress before retrying delivery.")),
                List.of(),
                List.of());

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(notificationService.deadLetterQueue(organizationId)).thenReturn(queue);

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/queue")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsRetry[0].recommendedAction").value("RETRY_DELIVERY"))
                .andExpect(jsonPath("$.needsRetry[0].recipientSuppressed").value(false))
                .andExpect(jsonPath("$.needsRetry[0].notification.id").value(notificationId.toString()))
                .andExpect(jsonPath("$.needsUnsuppress[0].recommendedAction").value("UNSUPPRESS_AND_RETRY"))
                .andExpect(jsonPath("$.needsUnsuppress[0].suppression.suppressionId").value(suppressionId.toString()))
                .andExpect(jsonPath("$.needsUnsuppress[0].recommendationReason").value("Unsuppress before retrying delivery."));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(notificationService).deadLetterQueue(organizationId);
    }

    @Test
    void resolvedDeadLetterHistoryMapsNotificationListIntoResponseBody() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationSummary notification = new NotificationSummary(
                notificationId,
                null,
                actorUserId,
                NotificationCategory.WORKFLOW,
                NotificationChannel.EMAIL,
                NotificationStatus.FAILED,
                NotificationDeliveryState.FAILED,
                "Dead-letter resolved without resend.",
                "WORKFLOW_TASK",
                "task-123",
                "ops@acme.test",
                "sendgrid",
                null,
                2,
                "Mailbox unavailable",
                "550",
                DeadLetterResolutionStatus.RESOLVED,
                DeadLetterResolutionReasonCode.DELIVERY_NO_LONGER_REQUIRED,
                "Handled externally; no resend needed.",
                Instant.parse("2026-04-22T17:00:00Z"),
                actorUserId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-04-23T10:00:00Z"),
                Instant.parse("2026-04-22T15:05:00Z"),
                null,
                Instant.parse("2026-04-22T14:55:00Z"));

        when(tenantAccessService.requireRole(organizationId, Set.of(UserRole.OWNER, UserRole.ADMIN))).thenReturn(actorUserId);
        when(notificationService.resolvedDeadLetters(organizationId)).thenReturn(List.of(notification));

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/history")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$[0].deadLetterResolutionStatus").value("RESOLVED"))
                .andExpect(jsonPath("$[0].deadLetterResolutionReasonCode").value("DELIVERY_NO_LONGER_REQUIRED"))
                .andExpect(jsonPath("$[0].deadLetterResolutionNote").value("Handled externally; no resend needed."));

        verify(tenantAccessService).requireRole(eq(organizationId), eq(Set.of(UserRole.OWNER, UserRole.ADMIN)));
        verify(notificationService).resolvedDeadLetters(organizationId);
    }
}
