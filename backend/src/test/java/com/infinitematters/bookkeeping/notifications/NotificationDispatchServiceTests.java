package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.users.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTests {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationDeliveryGateway deliveryGateway;

    @Mock
    private NotificationSuppressionService suppressionService;

    private NotificationDispatchService notificationDispatchService;

    @BeforeEach
    void setUp() {
        notificationDispatchService = new NotificationDispatchService(
                notificationRepository,
                List.of(deliveryGateway),
                suppressionService,
                auditService,
                3,
                Duration.ofMinutes(10));
    }

    @Test
    void dispatchesPendingEmailNotification() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AppUser user = new AppUser();
        setId(user, userId);

        Notification notification = new Notification();
        setId(notification, notificationId);
        notification.setUser(user);
        notification.setCategory(NotificationCategory.PASSWORD_RESET);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setMessage("Reset your password");
        notification.setScheduledFor(Instant.now().minusSeconds(10));

        when(notificationRepository.findTop100ByStatusAndScheduledForBeforeOrderByCreatedAtAsc(
                eq(NotificationStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(notification));
        when(deliveryGateway.supports(NotificationChannel.EMAIL)).thenReturn(true);
        when(deliveryGateway.deliver(notification))
                .thenReturn(new NotificationDeliveryReceipt("test-provider", "provider-message-123"));

        int dispatchedCount = notificationDispatchService.dispatchPendingNotifications();

        assertThat(dispatchedCount).isEqualTo(1);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getDeliveryState()).isEqualTo(NotificationDeliveryState.ACCEPTED);
        assertThat(notification.getSentAt()).isNotNull();
        assertThat(notification.getProviderName()).isEqualTo("test-provider");
        assertThat(notification.getProviderMessageId()).isEqualTo("provider-message-123");
        verify(deliveryGateway).deliver(notification);
        verify(notificationRepository).save(notification);
        verify(auditService).record(eq(null), eq("NOTIFICATION_DISPATCHED"), eq("notification"), eq(notificationId.toString()), any());
    }

    @Test
    void reschedulesNotificationWhenDeliveryFailsBeforeMaxAttempts() {
        UUID notificationId = UUID.randomUUID();

        Notification notification = new Notification();
        setId(notification, notificationId);
        notification.setCategory(NotificationCategory.PASSWORD_RESET);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setMessage("Reset your password");
        notification.setAttemptCount(0);
        notification.setScheduledFor(Instant.now().minusSeconds(10));

        when(notificationRepository.findTop100ByStatusAndScheduledForBeforeOrderByCreatedAtAsc(
                eq(NotificationStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(notification));
        when(deliveryGateway.supports(NotificationChannel.EMAIL)).thenReturn(true);
        doThrow(new NotificationDeliveryException("SendGrid rate limit reached", true, "RATE_LIMITED"))
                .when(deliveryGateway).deliver(notification);

        int dispatchedCount = notificationDispatchService.dispatchPendingNotifications();

        assertThat(dispatchedCount).isZero();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getDeliveryState()).isEqualTo(NotificationDeliveryState.PENDING);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
        assertThat(notification.getLastAttemptedAt()).isNotNull();
        assertThat(notification.getLastError()).isEqualTo("SendGrid rate limit reached");
        assertThat(notification.getProviderName()).isNull();
        assertThat(notification.getProviderMessageId()).isNull();
        assertThat(notification.getScheduledFor()).isAfter(notification.getLastAttemptedAt());
        verify(notificationRepository).save(notification);
        verify(auditService).record(eq(null), eq("NOTIFICATION_DISPATCH_RATE_LIMITED"), eq("notification"), eq(notificationId.toString()), any());
    }

    @Test
    void marksNotificationFailedWhenMaxAttemptsReached() {
        UUID notificationId = UUID.randomUUID();

        Notification notification = new Notification();
        setId(notification, notificationId);
        notification.setCategory(NotificationCategory.PASSWORD_RESET);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setMessage("Reset your password");
        notification.setAttemptCount(2);
        notification.setScheduledFor(Instant.now().minusSeconds(10));

        when(notificationRepository.findTop100ByStatusAndScheduledForBeforeOrderByCreatedAtAsc(
                eq(NotificationStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(notification));
        when(deliveryGateway.supports(NotificationChannel.EMAIL)).thenReturn(true);
        doThrow(new IllegalStateException("provider unavailable")).when(deliveryGateway).deliver(notification);

        int dispatchedCount = notificationDispatchService.dispatchPendingNotifications();

        assertThat(dispatchedCount).isZero();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getDeliveryState()).isEqualTo(NotificationDeliveryState.FAILED);
        assertThat(notification.getAttemptCount()).isEqualTo(3);
        assertThat(notification.getLastError()).isEqualTo("provider unavailable");
        assertThat(notification.getProviderName()).isNull();
        assertThat(notification.getProviderMessageId()).isNull();
        verify(notificationRepository).save(notification);
        verify(auditService).record(eq(null), eq("NOTIFICATION_DISPATCH_FAILED"), eq("notification"), eq(notificationId.toString()), any());
    }

    @Test
    void marksNotificationFailedImmediatelyForPermanentProviderRejection() {
        UUID notificationId = UUID.randomUUID();

        Notification notification = new Notification();
        setId(notification, notificationId);
        AppUser user = new AppUser();
        setId(user, UUID.randomUUID());
        user.setEmail("owner@acme.test");
        notification.setUser(user);
        notification.setCategory(NotificationCategory.PASSWORD_RESET);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setAttemptCount(0);
        notification.setScheduledFor(Instant.now().minusSeconds(10));

        when(notificationRepository.findTop100ByStatusAndScheduledForBeforeOrderByCreatedAtAsc(
                eq(NotificationStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(notification));
        when(suppressionService.isSuppressed("owner@acme.test", "sendgrid")).thenReturn(false);
        when(deliveryGateway.supports(NotificationChannel.EMAIL)).thenReturn(true);
        doThrow(new NotificationDeliveryException("SendGrid rejected request: 400", false, "PROVIDER_REJECTED"))
                .when(deliveryGateway).deliver(notification);

        int dispatchedCount = notificationDispatchService.dispatchPendingNotifications();

        assertThat(dispatchedCount).isZero();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
        assertThat(notification.getLastError()).isEqualTo("SendGrid rejected request: 400");
        verify(auditService).record(eq(null), eq("NOTIFICATION_DISPATCH_PERMANENT_FAILURE"), eq("notification"), eq(notificationId.toString()), any());
    }

    @Test
    void skipsSuppressedEmailDestination() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AppUser user = new AppUser();
        setId(user, userId);
        user.setEmail("owner@acme.test");

        Notification notification = new Notification();
        setId(notification, notificationId);
        notification.setUser(user);
        notification.setCategory(NotificationCategory.PASSWORD_RESET);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setScheduledFor(Instant.now().minusSeconds(10));

        when(notificationRepository.findTop100ByStatusAndScheduledForBeforeOrderByCreatedAtAsc(
                eq(NotificationStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(notification));
        when(suppressionService.isSuppressed("owner@acme.test", "sendgrid")).thenReturn(true);

        int dispatchedCount = notificationDispatchService.dispatchPendingNotifications();

        assertThat(dispatchedCount).isZero();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getLastError()).isEqualTo("Recipient suppressed by provider");
        verify(notificationRepository).save(notification);
        verify(auditService).record(eq(null), eq("NOTIFICATION_DELIVERY_SUPPRESSED"), eq("notification"), eq(notificationId.toString()), any());
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
