package com.infinitematters.bookkeeping.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventSummary(
        UUID id,
        UUID organizationId,
        UUID actorUserId,
        String eventType,
        String entityType,
        String entityId,
        String details,
        Instant createdAt) {
}
