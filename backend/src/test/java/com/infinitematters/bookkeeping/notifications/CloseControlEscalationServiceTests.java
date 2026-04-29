package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.UserRole;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.workflows.ReviewQueueService;
import com.infinitematters.bookkeeping.workflows.ReviewTaskSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloseControlEscalationServiceTests {

    @Mock
    private ReviewQueueService reviewQueueService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    private CloseControlEscalationService service;

    @BeforeEach
    void setUp() {
        service = new CloseControlEscalationService(
                reviewQueueService,
                notificationRepository,
                organizationService,
                userService,
                auditService);
    }

    @Test
    void escalatesReviewedAttestationAfterRepeatedReminders() {
        UUID organizationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Organization organization = organization(organizationId);
        AppUser owner = user(recipientId, "owner@acme.test", "Owner");

        ReviewTaskSummary task = new ReviewTaskSummary(
                taskId,
                null,
                null,
                "CLOSE_ATTESTATION_FOLLOW_UP",
                "HIGH",
                false,
                "Confirm month-end attestation for 2026-04",
                "Awaiting final confirmation",
                LocalDate.now(),
                UUID.randomUUID(),
                "Approver",
                null,
                null,
                null,
                null,
                0.0,
                null,
                "/close?month=2026-04",
                null,
                UUID.randomUUID(),
                Instant.now().minusSeconds(60),
                null,
                null,
                null);

        when(organizationService.get(organizationId)).thenReturn(organization);
        when(reviewQueueService.listCloseControlAttentionTasks(organizationId)).thenReturn(List.of(task));
        when(notificationRepository.countByOrganizationIdAndReferenceTypeAndReferenceId(
                organizationId,
                "close_control_follow_up",
                taskId.toString())).thenReturn(2L);
        when(userService.membersForOrganizationWithRoles(organizationId, List.of(UserRole.OWNER, UserRole.ADMIN)))
                .thenReturn(List.of(owner));
        when(notificationRepository.existsByOrganizationIdAndUserIdAndReferenceTypeAndReferenceIdAndStatusAndScheduledForAfter(
                eq(organizationId),
                eq(recipientId),
                eq("close_control_follow_up_escalation"),
                eq(taskId.toString()),
                eq(NotificationStatus.SENT),
                any())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            setId(saved, UUID.randomUUID());
            return saved;
        });

        CloseControlEscalationRunResult result = service.run(organizationId);

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.notifications()).singleElement().satisfies(notification -> {
            assertThat(notification.userId()).isEqualTo(recipientId);
            assertThat(notification.referenceType()).isEqualTo("close_control_follow_up_escalation");
            assertThat(notification.referenceId()).isEqualTo(taskId.toString());
            assertThat(notification.message()).contains("attestation for 2026-04");
        });
        verify(auditService).record(
                eq(organizationId),
                eq("CLOSE_CONTROL_ESCALATED"),
                eq("workflow_task"),
                eq(taskId.toString()),
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

    private void setId(Object target, UUID id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
