package com.infinitematters.bookkeeping.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryEventRepository extends JpaRepository<NotificationDeliveryEvent, UUID> {
    boolean existsByExternalEventId(String externalEventId);
    Optional<NotificationDeliveryEvent> findByExternalEventId(String externalEventId);
}
