package com.infinitematters.bookkeeping.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    List<AuditEvent> findByActorUserIdAndOrganizationIsNullOrderByCreatedAtDesc(UUID actorUserId);
    List<AuditEvent> findTop5ByOrganizationIdAndEventTypeOrderByCreatedAtDesc(UUID organizationId, String eventType);
    List<AuditEvent> findByOrganizationIdAndEventTypeAndEntityIdOrderByCreatedAtDesc(UUID organizationId, String eventType, String entityId);
    long countByOrganizationIdAndEventTypeAndCreatedAtAfter(UUID organizationId, String eventType, Instant createdAtAfter);
    boolean existsByOrganizationIdAndEventTypeAndEntityId(UUID organizationId, String eventType, String entityId);
}
