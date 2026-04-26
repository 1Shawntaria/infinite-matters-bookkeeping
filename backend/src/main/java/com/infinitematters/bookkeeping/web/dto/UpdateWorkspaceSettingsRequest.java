package com.infinitematters.bookkeeping.web.dto;

public record UpdateWorkspaceSettingsRequest(
        String name,
        String timezone,
        Integer invitationTtlDays) {
}
