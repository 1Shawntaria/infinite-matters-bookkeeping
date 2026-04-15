package com.infinitematters.bookkeeping.notifications;

public enum DeadLetterRecommendedAction {
    RETRY_DELIVERY,
    UNSUPPRESS_AND_RETRY,
    REVIEW_ACKNOWLEDGED,
    NONE
}
