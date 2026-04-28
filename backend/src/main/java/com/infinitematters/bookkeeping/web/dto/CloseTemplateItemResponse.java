package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.organization.OrganizationCloseTemplateItem;

import java.time.Instant;
import java.util.UUID;

public record CloseTemplateItemResponse(
        UUID id,
        String label,
        String guidance,
        int sortOrder,
        Instant createdAt) {
    public static CloseTemplateItemResponse from(OrganizationCloseTemplateItem item) {
        return new CloseTemplateItemResponse(
                item.getId(),
                item.getLabel(),
                item.getGuidance(),
                item.getSortOrder(),
                item.getCreatedAt());
    }
}
