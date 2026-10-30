package com.aechak.admin.review.response;

import com.aechak.application.review.admin.usecase.result.AdminReviewSummaryResult;
import com.aechak.domain.review.review.enums.ReviewBlindActor;
import com.aechak.domain.review.review.enums.ReviewBlindReason;
import com.aechak.domain.review.review.enums.ReviewStatus;
import java.time.LocalDateTime;

/**
 * content는 작성자가 쓴 원문, visibleContent는 구매자에게 실제로 보이는 문장이다 —
 * 마스킹된 리뷰에서 둘이 갈린다. blindActor가 차 있으면 그 처리를 누가 했는지다.
 */
public record AdminReviewSummaryResponse(
        long reviewId,
        String productId,
        String productName,
        String sellerName,
        long authorUserId,
        String authorNickname,
        boolean authorWithdrawn,
        int rating,
        String content,
        String visibleContent,
        long photoCount,
        long reportCount,
        ReviewStatus reviewStatus,
        ReviewBlindActor blindActor,
        ReviewBlindReason blindReasonCode,
        String blindReasonText,
        LocalDateTime blindedAt,
        LocalDateTime createdAt) {

    public static AdminReviewSummaryResponse from(AdminReviewSummaryResult result) {
        return new AdminReviewSummaryResponse(
                result.reviewId(),
                result.productPublicId(),
                result.productName(),
                result.sellerName(),
                result.authorUserId(),
                result.authorNickname(),
                result.authorWithdrawn(),
                result.rating(),
                result.content(),
                result.visibleContent(),
                result.photoCount(),
                result.reportCount(),
                result.reviewStatus(),
                result.blindActor(),
                result.blindReasonCode(),
                result.blindReasonText(),
                result.blindedAt(),
                result.createdAt());
    }
}
