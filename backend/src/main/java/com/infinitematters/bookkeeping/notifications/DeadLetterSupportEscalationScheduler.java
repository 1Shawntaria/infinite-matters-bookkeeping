package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.organization.OrganizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "bookkeeping.notifications.dead-letter-escalations.scheduler.enabled", havingValue = "true")
public class DeadLetterSupportEscalationScheduler {
    private final DeadLetterSupportEscalationService deadLetterSupportEscalationService;
    private final OrganizationService organizationService;

    public DeadLetterSupportEscalationScheduler(DeadLetterSupportEscalationService deadLetterSupportEscalationService,
                                                OrganizationService organizationService) {
        this.deadLetterSupportEscalationService = deadLetterSupportEscalationService;
        this.organizationService = organizationService;
    }

    @Scheduled(fixedDelayString = "${bookkeeping.notifications.dead-letter-escalations.scheduler.fixed-delay-ms:300000}")
    public void run() {
        organizationService.list().forEach(organization ->
                deadLetterSupportEscalationService.run(organization.getId()));
    }
}
