package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.notifications.DeadLetterResolutionReasonCode;

public record ResolveDeadLetterNoResendRequest(DeadLetterResolutionReasonCode reasonCode, String note) {
}
