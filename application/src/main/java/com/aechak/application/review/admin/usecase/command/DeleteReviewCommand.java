package com.aechak.application.review.admin.usecase.command;

import com.aechak.domain.review.review.enums.ReviewBlindReason;

public record DeleteReviewCommand(long adminId, long reviewId, ReviewBlindReason reasonCode, String reasonText) {}
