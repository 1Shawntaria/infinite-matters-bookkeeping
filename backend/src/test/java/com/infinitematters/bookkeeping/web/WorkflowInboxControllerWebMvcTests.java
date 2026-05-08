package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.dashboard.DashboardActionUrgency;
import com.infinitematters.bookkeeping.domain.Category;
import com.infinitematters.bookkeeping.notifications.CloseControlDisposition;
import com.infinitematters.bookkeeping.notifications.CloseControlEscalationService;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportEscalationService;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceMonitorService;
import com.infinitematters.bookkeeping.notifications.DeadLetterWorkflowTaskService;
import com.infinitematters.bookkeeping.notifications.NotificationCategory;
import com.infinitematters.bookkeeping.notifications.NotificationChannel;
import com.infinitematters.bookkeeping.notifications.NotificationDeliveryState;
import com.infinitematters.bookkeeping.notifications.NotificationService;
import com.infinitematters.bookkeeping.notifications.NotificationStatus;
import com.infinitematters.bookkeeping.notifications.NotificationSummary;
import com.infinitematters.bookkeeping.notifications.NotificationSuppressionService;
import com.infinitematters.bookkeeping.security.BearerTokenAuthenticationFilter;
import com.infinitematters.bookkeeping.security.CsrfProtectionFilter;
import com.infinitematters.bookkeeping.security.RequestIdentityFilter;
import com.infinitematters.bookkeeping.security.RequestLoggingFilter;
import com.infinitematters.bookkeeping.security.TenantAccessService;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.workflows.CloseFollowUpSeverity;
import com.infinitematters.bookkeeping.workflows.ReviewQueueService;
import com.infinitematters.bookkeeping.workflows.ReviewTaskSummary;
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
}
