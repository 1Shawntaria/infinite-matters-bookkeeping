package com.infinitematters.bookkeeping.web.dto;

import java.math.BigDecimal;

public record UpdateWorkspaceSettingsRequest(
        String name,
        String timezone,
        Integer invitationTtlDays,
        BigDecimal closeMaterialityThreshold,
        Integer minimumCloseNotesRequired,
        Boolean requireSignoffBeforeClose,
        Integer minimumSignoffCount,
        Boolean requireOwnerSignoffBeforeClose,
        Boolean requireTemplateCompletionBeforeClose) {
}
