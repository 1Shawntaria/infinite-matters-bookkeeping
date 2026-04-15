package com.infinitematters.bookkeeping.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationSuppressionRepository extends JpaRepository<NotificationSuppression, UUID> {
    Optional<NotificationSuppression> findByEmailIgnoreCaseAndProviderNameAndActiveTrue(String email, String providerName);
    Optional<NotificationSuppression> findByIdAndActiveTrueAndSourceNotificationOrganizationId(UUID id, UUID organizationId);
    List<NotificationSuppression> findByActiveTrueAndSourceNotificationOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    long countByActiveTrue();
}
