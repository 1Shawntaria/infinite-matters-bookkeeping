package com.infinitematters.bookkeeping.notifications;

import com.infinitematters.bookkeeping.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationSuppressionService {
    private final NotificationSuppressionRepository suppressionRepository;
    private final AuditService auditService;

    public NotificationSuppressionService(NotificationSuppressionRepository suppressionRepository,
                                          AuditService auditService) {
        this.suppressionRepository = suppressionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public boolean isSuppressed(String email, String providerName) {
        if (email == null || email.isBlank() || providerName == null || providerName.isBlank()) {
            return false;
        }
        return suppressionRepository.findByEmailIgnoreCaseAndProviderNameAndActiveTrue(email, providerName).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<NotificationSuppressionSummary> activeSuppression(String email, String providerName) {
        if (email == null || email.isBlank() || providerName == null || providerName.isBlank()) {
            return Optional.empty();
        }
        return suppressionRepository.findByEmailIgnoreCaseAndProviderNameAndActiveTrue(email, providerName)
                .map(NotificationSuppressionSummary::from);
    }

    @Transactional
    public void suppress(String email,
                         String providerName,
                         String reason,
                         Notification sourceNotification,
                         Instant eventAt) {
        if (email == null || email.isBlank() || providerName == null || providerName.isBlank()) {
            return;
        }
        NotificationSuppression suppression = suppressionRepository
                .findByEmailIgnoreCaseAndProviderNameAndActiveTrue(email, providerName)
                .orElseGet(NotificationSuppression::new);
        suppression.setEmail(email);
        suppression.setProviderName(providerName);
        suppression.setReason(reason);
        suppression.setSourceNotification(sourceNotification);
        suppression.setActive(true);
        suppression.setLastEventAt(eventAt != null ? eventAt : Instant.now());
        suppressionRepository.save(suppression);
    }

    @Transactional(readOnly = true)
    public long activeSuppressionCount() {
        return suppressionRepository.countByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<NotificationSuppressionSummary> listActiveSuppressions(UUID organizationId) {
        return suppressionRepository.findByActiveTrueAndSourceNotificationOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(NotificationSuppressionSummary::from)
                .toList();
    }

    @Transactional
    public NotificationSuppressionSummary deactivate(UUID organizationId, UUID suppressionId) {
        NotificationSuppression suppression = suppressionRepository
                .findByIdAndActiveTrueAndSourceNotificationOrganizationId(suppressionId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification suppression: " + suppressionId));
        suppression.setActive(false);
        suppressionRepository.save(suppression);
        auditService.record(organizationId,
                "NOTIFICATION_SUPPRESSION_DEACTIVATED",
                "notification_suppression",
                suppressionId.toString(),
                "Notification suppression deactivated for " + suppression.getEmail());
        return NotificationSuppressionSummary.from(suppression);
    }
}
