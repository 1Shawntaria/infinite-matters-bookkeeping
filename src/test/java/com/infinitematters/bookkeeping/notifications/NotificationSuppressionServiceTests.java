package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class NotificationSuppressionServiceTests {

    @Mock
    private NotificationSuppressionRepository suppressionRepository;

    @Mock
    private AuditService auditService;

    private NotificationSuppressionService suppressionService;

    @BeforeEach
    void setUp() {
        suppressionService = new NotificationSuppressionService(suppressionRepository, auditService);
    }

    @Test
    void listsAndDeactivatesOrganizationSuppressions() {
        UUID organizationId = UUID.randomUUID();
        UUID suppressionId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        Organization organization = new Organization();
        setId(organization, organizationId);

        Notification notification = new Notification();
        setId(notification, notificationId);
        notification.setOrganization(organization);

        NotificationSuppression suppression = new NotificationSuppression();
        setId(suppression, suppressionId);
        suppression.setEmail("owner@acme.test");
        suppression.setProviderName("sendgrid");
        suppression.setReason("BOUNCED");
        suppression.setSourceNotification(notification);
        suppression.setActive(true);
        suppression.setLastEventAt(Instant.now());

        when(suppressionRepository.findByActiveTrueAndSourceNotificationOrganizationIdOrderByCreatedAtDesc(organizationId))
                .thenReturn(List.of(suppression));
        when(suppressionRepository.findByIdAndActiveTrueAndSourceNotificationOrganizationId(suppressionId, organizationId))
                .thenReturn(Optional.of(suppression));

        List<NotificationSuppressionSummary> listed = suppressionService.listActiveSuppressions(organizationId);
        NotificationSuppressionSummary deactivated = suppressionService.deactivate(organizationId, suppressionId);

        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).email()).isEqualTo("owner@acme.test");
        assertThat(deactivated.suppressionId()).isEqualTo(suppressionId);
        assertThat(suppression.isActive()).isFalse();
        verify(suppressionRepository).save(suppression);
        verify(auditService).record(eq(organizationId), eq("NOTIFICATION_SUPPRESSION_DEACTIVATED"), eq("notification_suppression"), eq(suppressionId.toString()), any());
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
