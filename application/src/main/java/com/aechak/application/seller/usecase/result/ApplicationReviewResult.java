package com.aechak.application.seller.usecase.result;

import com.aechak.domain.seller.application.ApplicationReview;
import com.aechak.domain.seller.application.enums.ReviewDecision;
import java.time.LocalDateTime;

public record ApplicationReviewResult(ReviewDecision decision, String rejectionReason, LocalDateTime reviewedAt) {

    public static ApplicationReviewResult from(ApplicationReview review) {
        return new ApplicationReviewResult(review.getDecision(), review.getRejectionReason(), review.getCreatedAt());
    }
}
