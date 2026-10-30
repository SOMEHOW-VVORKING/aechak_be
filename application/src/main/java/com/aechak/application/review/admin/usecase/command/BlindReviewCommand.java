package com.aechak.application.review.admin.usecase.command;

import com.aechak.domain.review.review.enums.ReviewBlindReason;

/** reasonText는 운영자 메모로 선택이다. 사유 코드는 필수 — 코드 없이는 왜 가렸는지 남지 않는다. */
public record BlindReviewCommand(long adminId, long reviewId, ReviewBlindReason reasonCode, String reasonText) {}
