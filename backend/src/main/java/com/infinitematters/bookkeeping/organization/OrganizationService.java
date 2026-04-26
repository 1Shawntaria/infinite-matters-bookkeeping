package com.infinitematters.bookkeeping.organization;

import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {
    public static final int DEFAULT_INVITATION_TTL_DAYS = 7;
    public static final int MIN_INVITATION_TTL_DAYS = 1;
    public static final int MAX_INVITATION_TTL_DAYS = 30;

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
        return repository.save(organization);
    }

    public Organization updateSettings(UUID organizationId, String name, String timezone, Integer invitationTtlDays) {
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
