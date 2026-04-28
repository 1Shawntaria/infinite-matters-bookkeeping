package com.infinitematters.bookkeeping.organization;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {
    public static final int DEFAULT_INVITATION_TTL_DAYS = 7;
    public static final int MIN_INVITATION_TTL_DAYS = 1;
    public static final int MAX_INVITATION_TTL_DAYS = 30;
    public static final BigDecimal DEFAULT_CLOSE_MATERIALITY_THRESHOLD = new BigDecimal("500.00");
    public static final BigDecimal MIN_CLOSE_MATERIALITY_THRESHOLD = new BigDecimal("0.00");
    public static final BigDecimal MAX_CLOSE_MATERIALITY_THRESHOLD = new BigDecimal("1000000.00");
    public static final int DEFAULT_MINIMUM_CLOSE_NOTES_REQUIRED = 1;
    public static final int MIN_MINIMUM_CLOSE_NOTES_REQUIRED = 0;
    public static final int MAX_MINIMUM_CLOSE_NOTES_REQUIRED = 10;
    public static final int DEFAULT_MINIMUM_SIGNOFF_COUNT = 1;
    public static final int MIN_MINIMUM_SIGNOFF_COUNT = 0;
    public static final int MAX_MINIMUM_SIGNOFF_COUNT = 10;

    private final OrganizationRepository repository;

    public OrganizationService(OrganizationRepository repository) {
        this.repository = repository;
    }

    public Organization create(String name, PlanTier planTier, String timezone) {
        Organization organization = new Organization();
        organization.setName(normalizeName(name));
        organization.setPlanTier(planTier);
        organization.setTimezone(normalizeTimezone(timezone));
        organization.setInvitationTtlDays(DEFAULT_INVITATION_TTL_DAYS);
        organization.setCloseMaterialityThreshold(DEFAULT_CLOSE_MATERIALITY_THRESHOLD);
        organization.setMinimumCloseNotesRequired(DEFAULT_MINIMUM_CLOSE_NOTES_REQUIRED);
        organization.setRequireSignoffBeforeClose(true);
        organization.setMinimumSignoffCount(DEFAULT_MINIMUM_SIGNOFF_COUNT);
        organization.setRequireOwnerSignoffBeforeClose(false);
        return repository.save(organization);
    }

    public Organization updateSettings(UUID organizationId,
                                       String name,
                                       String timezone,
                                       Integer invitationTtlDays,
                                       BigDecimal closeMaterialityThreshold,
                                       Integer minimumCloseNotesRequired,
                                       Boolean requireSignoffBeforeClose,
                                       Integer minimumSignoffCount,
                                       Boolean requireOwnerSignoffBeforeClose,
                                       Boolean requireTemplateCompletionBeforeClose) {
        Organization organization = get(organizationId);

        if (name != null) {
            organization.setName(normalizeName(name));
        }
        if (timezone != null) {
            organization.setTimezone(normalizeTimezone(timezone));
        }
        if (invitationTtlDays != null) {
            organization.setInvitationTtlDays(validateInvitationTtl(invitationTtlDays));
        }
        if (closeMaterialityThreshold != null) {
            organization.setCloseMaterialityThreshold(validateCloseMaterialityThreshold(closeMaterialityThreshold));
        }
        if (minimumCloseNotesRequired != null) {
            organization.setMinimumCloseNotesRequired(validateMinimumCloseNotesRequired(minimumCloseNotesRequired));
        }
        if (requireSignoffBeforeClose != null) {
            organization.setRequireSignoffBeforeClose(requireSignoffBeforeClose);
        }
        if (minimumSignoffCount != null) {
            organization.setMinimumSignoffCount(validateMinimumSignoffCount(minimumSignoffCount));
        }
        if (requireOwnerSignoffBeforeClose != null) {
            organization.setRequireOwnerSignoffBeforeClose(requireOwnerSignoffBeforeClose);
        }
        if (requireTemplateCompletionBeforeClose != null) {
            organization.setRequireTemplateCompletionBeforeClose(requireTemplateCompletionBeforeClose);
        }
        return repository.save(organization);
    }

    public List<Organization> list() {
        return repository.findAll();
    }

    public List<Organization> listByIds(List<UUID> organizationIds) {
        return repository.findAllById(organizationIds);
    }

    public Organization get(UUID organizationId) {
        return repository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown organization: " + organizationId));
    }

    private static int validateInvitationTtl(int invitationTtlDays) {
        if (invitationTtlDays < MIN_INVITATION_TTL_DAYS || invitationTtlDays > MAX_INVITATION_TTL_DAYS) {
            throw new IllegalArgumentException(
                    "Invitation TTL must be between " + MIN_INVITATION_TTL_DAYS + " and " + MAX_INVITATION_TTL_DAYS + " days");
        }
        return invitationTtlDays;
    }

    private static BigDecimal validateCloseMaterialityThreshold(BigDecimal closeMaterialityThreshold) {
        if (closeMaterialityThreshold.scale() > 2) {
            throw new IllegalArgumentException("Close materiality threshold must use two decimal places or fewer");
        }
        if (closeMaterialityThreshold.compareTo(MIN_CLOSE_MATERIALITY_THRESHOLD) < 0 ||
                closeMaterialityThreshold.compareTo(MAX_CLOSE_MATERIALITY_THRESHOLD) > 0) {
            throw new IllegalArgumentException("Close materiality threshold must be between 0.00 and 1000000.00");
        }
        return closeMaterialityThreshold.stripTrailingZeros().scale() < 0
                ? closeMaterialityThreshold.setScale(0)
                : closeMaterialityThreshold;
    }

    private static int validateMinimumCloseNotesRequired(int minimumCloseNotesRequired) {
        if (minimumCloseNotesRequired < MIN_MINIMUM_CLOSE_NOTES_REQUIRED ||
                minimumCloseNotesRequired > MAX_MINIMUM_CLOSE_NOTES_REQUIRED) {
            throw new IllegalArgumentException("Minimum close notes required must be between 0 and 10");
        }
        return minimumCloseNotesRequired;
    }

    private static int validateMinimumSignoffCount(int minimumSignoffCount) {
        if (minimumSignoffCount < MIN_MINIMUM_SIGNOFF_COUNT ||
                minimumSignoffCount > MAX_MINIMUM_SIGNOFF_COUNT) {
            throw new IllegalArgumentException("Minimum signoff count must be between 0 and 10");
        }
        return minimumSignoffCount;
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Organization name is required");
        }
        String normalized = name.trim();
        if (normalized.length() > 120) {
            throw new IllegalArgumentException("Organization name must be 120 characters or fewer");
        }
        return normalized;
    }

    private static String normalizeTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("Timezone is required");
        }
        String normalized = timezone.trim();
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("Timezone must be 64 characters or fewer");
        }
        try {
            ZoneId.of(normalized);
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Timezone must be a valid IANA zone ID");
        }
        return normalized;
    }
}
