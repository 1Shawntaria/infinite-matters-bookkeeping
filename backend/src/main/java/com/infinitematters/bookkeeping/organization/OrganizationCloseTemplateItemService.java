package com.infinitematters.bookkeeping.organization;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OrganizationCloseTemplateItemService {
    private final OrganizationCloseTemplateItemRepository repository;
    private final OrganizationService organizationService;

    public OrganizationCloseTemplateItemService(OrganizationCloseTemplateItemRepository repository,
                                                OrganizationService organizationService) {
        this.repository = repository;
        this.organizationService = organizationService;
    }

    public List<OrganizationCloseTemplateItem> list(UUID organizationId) {
        organizationService.get(organizationId);
        return repository.findByOrganizationIdOrderBySortOrderAscCreatedAtAsc(organizationId);
    }

    public OrganizationCloseTemplateItem create(UUID organizationId, String label, String guidance) {
        Organization organization = organizationService.get(organizationId);
        OrganizationCloseTemplateItem item = new OrganizationCloseTemplateItem();
        item.setOrganization(organization);
        item.setLabel(normalizeLabel(label));
        item.setGuidance(normalizeGuidance(guidance));
        Integer maxSortOrder = repository.findMaxSortOrderByOrganizationId(organizationId);
        item.setSortOrder(maxSortOrder == null ? 1 : maxSortOrder + 1);
        return repository.save(item);
    }

    public void delete(UUID organizationId, UUID itemId) {
        OrganizationCloseTemplateItem item = repository.findByIdAndOrganizationId(itemId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown close template item: " + itemId));
        repository.delete(item);
    }

    private static String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Close template label is required");
        }
        String normalized = label.trim();
        if (normalized.length() > 120) {
            throw new IllegalArgumentException("Close template label must be 120 characters or fewer");
        }
        return normalized;
    }

    private static String normalizeGuidance(String guidance) {
        if (guidance == null || guidance.isBlank()) {
            throw new IllegalArgumentException("Close template guidance is required");
        }
        String normalized = guidance.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("Close template guidance must be 500 characters or fewer");
        }
        return normalized;
    }
}
