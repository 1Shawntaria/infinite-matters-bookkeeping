package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.users.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationProviderEventServiceTests {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationDeliveryEventRepository deliveryEventRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationSuppressionService suppressionService;

    private NotificationProviderEventService notificationProviderEventService;

    @BeforeEach
    void setUp() {
        notificationProviderEventService = new NotificationProviderEventService(
                notificationRepository,
                deliveryEventRepository,
                suppressionService,
                auditService);
    }

    @Test
    void marksNotificationDeliveredWhenProviderReportsDelivered() {
        UUID organizationId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        Organization organization = new Organization();
        setId(organization, organizationId);

        Notification notification = new Notification();
        setId(notification, notificationId);
        notification.setOrganization(organization);
        notification.setStatus(NotificationStatus.SENT);
        notification.setDeliveryState(NotificationDeliveryState.ACCEPTED);
        notification.setProviderName("test-provider");
        notification.setProviderMessageId("provider-message-123");

        when(deliveryEventRepository.existsByExternalEventId("evt-1")).thenReturn(false);
        when(notificationRepository.findByProviderNameAndProviderMessageId("test-provider", "provider-message-123"))
                .thenReturn(Optional.of(notification));

        NotificationSummary summary = notificationProviderEventService.ingestEvent(
                "test-provider",
                "provider-message-123",
                "DELIVERED",
                "evt-1",
                Instant.now(),
                "provider delivered",
                "{\"event\":\"delivered\"}",
                new VerifiedProviderEvent("SHARED_SECRET", "secret"));

        assertThat(summary.deliveryState()).isEqualTo(NotificationDeliveryState.DELIVERED);
        assertThat(summary.status()).isEqualTo(NotificationStatus.SENT);
        verify(deliveryEventRepository).save(any(NotificationDeliveryEvent.class));
        verify(notificationRepository).save(notification);
        verify(auditService).record(eq(organizationId), eq("NOTIFICATION_PROVIDER_EVENT_INGESTED"), eq("notification"), eq(notificationId.toString()), any());
    }

    @Test
    void suppressesDestinationWhenProviderReportsBounce() {
        Notification notification = new Notification();
        AppUser user = new AppUser();
        setId(notification, UUID.randomUUID());
        setId(user, UUID.randomUUID());
        user.setEmail("owner@acme.test");
        notification.setUser(user);
        notification.setStatus(NotificationStatus.SENT);
        notification.setDeliveryState(NotificationDeliveryState.ACCEPTED);
        notification.setProviderName("sendgrid");
        notification.setProviderMessageId("provider-message-123");

        when(deliveryEventRepository.existsByExternalEventId("evt-2")).thenReturn(false);
        when(notificationRepository.findByProviderNameAndProviderMessageId("sendgrid", "provider-message-123"))
                .thenReturn(Optional.of(notification));

        NotificationSummary summary = notificationProviderEventService.ingestEvent(
                "sendgrid",
                "provider-message-123",
                "BOUNCED",
                "evt-2",
                Instant.now(),
                "provider bounced",
                "{\"event\":\"bounce\"}",
                new VerifiedProviderEvent("SENDGRID_SIGNATURE", "1772442060"));

        assertThat(summary.deliveryState()).isEqualTo(NotificationDeliveryState.BOUNCED);
        assertThat(summary.status()).isEqualTo(NotificationStatus.FAILED);
        verify(suppressionService).suppress(eq("owner@acme.test"), eq("sendgrid"), eq("BOUNCED"), eq(notification), any());
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
