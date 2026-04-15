package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.users.AppUser;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendGridEmailNotificationProviderTests {

    @Test
    void delegatesDeliveryToSendGridApiClient() {
        SendGridApiClient apiClient = mock(SendGridApiClient.class);
        SendGridEmailNotificationProvider provider =
                new SendGridEmailNotificationProvider(apiClient, "books@acme.test");

        Notification notification = new Notification();
        AppUser user = new AppUser();
        setId(notification, UUID.randomUUID());
        setId(user, UUID.randomUUID());
        user.setEmail("owner@acme.test");
        notification.setUser(user);
        notification.setCategory(NotificationCategory.PASSWORD_RESET);
        notification.setMessage("Reset your password");

        NotificationDeliveryReceipt expectedReceipt =
                new NotificationDeliveryReceipt("sendgrid", "sg-message-1");
        when(apiClient.send(notification, "books@acme.test")).thenReturn(expectedReceipt);

        NotificationDeliveryReceipt receipt = provider.send(notification);

        assertThat(receipt).isEqualTo(expectedReceipt);
        verify(apiClient).send(notification, "books@acme.test");
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
