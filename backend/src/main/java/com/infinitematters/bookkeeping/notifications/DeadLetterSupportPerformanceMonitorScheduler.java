package com.infinitematters.bookkeeping.notifications;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "bookkeeping.notifications.dead-letter-support-performance.scheduler.enabled", havingValue = "true")
public class DeadLetterSupportPerformanceMonitorScheduler {
    private final DeadLetterSupportPerformanceMonitorService deadLetterSupportPerformanceMonitorService;

    public DeadLetterSupportPerformanceMonitorScheduler(DeadLetterSupportPerformanceMonitorService deadLetterSupportPerformanceMonitorService) {
        this.deadLetterSupportPerformanceMonitorService = deadLetterSupportPerformanceMonitorService;
    }

    @Scheduled(fixedDelayString = "${bookkeeping.notifications.dead-letter-support-performance.scheduler.fixed-delay-ms:300000}")
    public void run() {
        deadLetterSupportPerformanceMonitorService.syncAllOrganizations();
    }
}
