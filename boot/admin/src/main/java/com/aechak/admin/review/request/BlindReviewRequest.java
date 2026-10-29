package com.aechak.admin.review.request;

import com.aechak.domain.review.review.Review;
import com.aechak.domain.review.review.enums.ReviewBlindReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 사유 코드는 필수, 메모는 선택이다. 메모는 작성자에게 보이지 않는 내부 기록이다. */
public record BlindReviewRequest(
        @NotNull(message = "처리 사유를 선택해 주세요.") ReviewBlindReason reasonCode,
        @Size(max = Review.BLIND_REASON_TEXT_MAX, message = "처리 사유 메모는 {max}자 이하여야 합니다.") String reasonText) {}
