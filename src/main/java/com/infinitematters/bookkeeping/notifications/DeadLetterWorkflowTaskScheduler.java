package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.organization.OrganizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "bookkeeping.notifications.dead-letter-tasks.scheduler.enabled", havingValue = "true")
public class DeadLetterWorkflowTaskScheduler {
    private final DeadLetterWorkflowTaskService deadLetterWorkflowTaskService;
    private final OrganizationService organizationService;

    public DeadLetterWorkflowTaskScheduler(DeadLetterWorkflowTaskService deadLetterWorkflowTaskService,
                                           OrganizationService organizationService) {
        this.deadLetterWorkflowTaskService = deadLetterWorkflowTaskService;
        this.organizationService = organizationService;
    }

    @Scheduled(fixedDelayString = "${bookkeeping.notifications.dead-letter-tasks.scheduler.fixed-delay-ms:300000}")
    public void run() {
        organizationService.list().forEach(organization ->
                deadLetterWorkflowTaskService.syncOrganization(organization.getId()));
    }
}
