package com.aechak.application.review.admin.port;

import com.aechak.domain.review.review.enums.ReviewStatus;

public record AdminReviewListCondition(
        String productPublicId,
        Integer rating,
        ReviewStatus status,
        boolean reportedOnly,
        String keyword,
        Long lastReviewId,
        int limit) {}
