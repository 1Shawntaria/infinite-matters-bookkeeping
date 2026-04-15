package com.infinitematters.bookkeeping.organization;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {
    private final OrganizationRepository repository;

    public OrganizationService(OrganizationRepository repository) {
        this.repository = repository;
    }

    public Organization create(String name, PlanTier planTier, String timezone) {
        Organization organization = new Organization();
        organization.setName(name);
        organization.setPlanTier(planTier);
        organization.setTimezone(timezone);
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
}
