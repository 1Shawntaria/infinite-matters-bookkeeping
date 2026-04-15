package com.infinitematters.bookkeeping.actuator;

import com.infinitematters.bookkeeping.notifications.NotificationSuppressionService;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class BookkeepingHealthIndicator implements HealthIndicator {
    private final OrganizationService organizationService;
    private final NotificationSuppressionService suppressionService;

    public BookkeepingHealthIndicator(OrganizationService organizationService,
                                      NotificationSuppressionService suppressionService) {
        this.organizationService = organizationService;
        this.suppressionService = suppressionService;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("organizations", organizationService.list().size())
                .withDetail("suppressedNotificationDestinations", suppressionService.activeSuppressionCount())
                .withDetail("service", "bookkeeping")
                .build();
    }
}
