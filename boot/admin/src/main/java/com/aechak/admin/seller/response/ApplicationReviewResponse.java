package com.aechak.admin.seller.response;

import com.aechak.application.seller.usecase.result.ApplicationReviewResult;
import com.aechak.domain.seller.application.enums.ReviewDecision;
import java.time.LocalDateTime;

public record ApplicationReviewResponse(ReviewDecision decision, String rejectionReason, LocalDateTime reviewedAt) {

    public static ApplicationReviewResponse from(ApplicationReviewResult result) {
        return new ApplicationReviewResponse(result.decision(), result.rejectionReason(), result.reviewedAt());
    }
}
