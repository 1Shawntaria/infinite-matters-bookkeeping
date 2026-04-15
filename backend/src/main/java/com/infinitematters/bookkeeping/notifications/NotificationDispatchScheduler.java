package com.infinitematters.bookkeeping.notifications;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "bookkeeping.notifications.dispatch.scheduler.enabled", havingValue = "true")
public class NotificationDispatchScheduler {
    private final NotificationDispatchService notificationDispatchService;

    public NotificationDispatchScheduler(NotificationDispatchService notificationDispatchService) {
        this.notificationDispatchService = notificationDispatchService;
    }

    @Scheduled(fixedDelayString = "${bookkeeping.notifications.dispatch.scheduler.fixed-delay-ms:300000}")
    public void run() {
        notificationDispatchService.dispatchPendingNotifications();
    }
}
