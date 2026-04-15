package com.infinitematters.bookkeeping.domain;

public record CategorizationResult(
        Category category, Confidence confidence, String route, String explanation) {}
