package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.organization.OrganizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "bookkeeping.reminders.scheduler.enabled", havingValue = "true")
public class ReminderScheduler {
    private final NotificationService notificationService;
    private final OrganizationService organizationService;

    public ReminderScheduler(NotificationService notificationService,
                             OrganizationService organizationService) {
        this.notificationService = notificationService;
        this.organizationService = organizationService;
    }

    @Scheduled(fixedDelayString = "${bookkeeping.reminders.scheduler.fixed-delay-ms:300000}")
    public void run() {
        organizationService.list().forEach(organization ->
                notificationService.generateTaskReminders(organization.getId()));
    }
}
