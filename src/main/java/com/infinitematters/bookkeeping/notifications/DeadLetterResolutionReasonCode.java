package com.infinitematters.bookkeeping.notifications;

public enum DeadLetterResolutionReasonCode {
    DUPLICATE_NOTIFICATION,
    USER_REQUESTED_NO_RESEND,
    DESTINATION_CORRECTED_EXTERNALLY,
    DELIVERY_NO_LONGER_REQUIRED,
    OTHER
}
