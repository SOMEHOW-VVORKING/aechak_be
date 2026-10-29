package com.aechak.application.review.admin.usecase.result;

import com.aechak.application.review.admin.port.view.AdminReviewView;
import com.aechak.domain.review.review.Review;
import com.aechak.domain.review.review.enums.ReviewBlindActor;
import com.aechak.domain.review.review.enums.ReviewBlindReason;
import com.aechak.domain.review.review.enums.ReviewStatus;
import java.time.LocalDateTime;

/**
 * 목록 한 줄. content는 운영자가 판단해야 하는 대상이라 마스킹 대체 본문이 아니라 원문을 싣고,
 * 구매자에게 실제로 보이는 문장은 visibleContent로 따로 싣는다.
 */
public record AdminReviewSummaryResult(
        long reviewId,
        String productPublicId,
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

    public static AdminReviewSummaryResult from(
            AdminReviewView view, String authorNickname, boolean authorWithdrawn, long photoCount, long reportCount) {
        return new AdminReviewSummaryResult(
                view.reviewId(),
                view.productPublicId(),
                view.productName(),
                view.sellerName(),
                view.authorUserId(),
                authorNickname,
                authorWithdrawn,
                view.rating(),
                view.content(),
                Review.Companion.visibleContent(view.reviewStatus(), view.content(), view.displayContent()),
                photoCount,
                reportCount,
                view.reviewStatus(),
                view.blindActor(),
                view.blindReasonCode(),
                view.blindReasonText(),
                view.blindedAt(),
                view.createdAt());
    }
}
