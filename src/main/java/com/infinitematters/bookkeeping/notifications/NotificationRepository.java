package com.infinitematters.bookkeeping.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Notification> findByOrganizationIdAndStatusOrderByCreatedAtDesc(UUID organizationId, NotificationStatus status);
    List<Notification> findTop100ByStatusAndScheduledForBeforeOrderByCreatedAtAsc(NotificationStatus status, Instant scheduledForBefore);
    List<Notification> findTop10ByOrganizationIdAndStatusInOrderByCreatedAtDesc(UUID organizationId, List<NotificationStatus> statuses);
    long countByOrganizationIdAndStatus(UUID organizationId, NotificationStatus status);
    long countByOrganizationIdAndDeliveryState(UUID organizationId, NotificationDeliveryState deliveryState);
    Optional<Notification> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<Notification> findByProviderNameAndProviderMessageId(String providerName, String providerMessageId);
    List<Notification> findByOrganizationIdAndReferenceTypeOrderByCreatedAtDesc(UUID organizationId, String referenceType);
    List<Notification> findByWorkflowTaskIdAndReferenceTypeOrderByCreatedAtAsc(UUID workflowTaskId, String referenceType);
    long countByWorkflowTaskIdAndReferenceType(UUID workflowTaskId, String referenceType);

    boolean existsByWorkflowTaskIdAndStatusAndScheduledForAfter(UUID workflowTaskId,
                                                                NotificationStatus status,
                                                                Instant scheduledAfter);

    boolean existsByWorkflowTaskIdAndUserIdAndReferenceTypeAndStatusAndScheduledForAfter(UUID workflowTaskId,
                                                                                          UUID userId,
                                                                                          String referenceType,
                                                                                          NotificationStatus status,
                                                                                          Instant scheduledAfter);
}
