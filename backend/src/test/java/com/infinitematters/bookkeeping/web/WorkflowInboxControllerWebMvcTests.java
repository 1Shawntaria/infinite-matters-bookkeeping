package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.dashboard.DashboardActionUrgency;
import com.infinitematters.bookkeeping.domain.Category;
import com.infinitematters.bookkeeping.notifications.CloseControlEscalationService;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportEscalationService;
import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceMonitorService;
import com.infinitematters.bookkeeping.notifications.DeadLetterWorkflowTaskService;
import com.infinitematters.bookkeeping.notifications.NotificationService;
import com.infinitematters.bookkeeping.notifications.NotificationSuppressionService;
import com.infinitematters.bookkeeping.security.BearerTokenAuthenticationFilter;
import com.infinitematters.bookkeeping.security.CsrfProtectionFilter;
import com.infinitematters.bookkeeping.security.RequestIdentityFilter;
import com.infinitematters.bookkeeping.security.RequestLoggingFilter;
import com.infinitematters.bookkeeping.security.TenantAccessService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
