package com.aechak.application.review.admin.usecase.result;

import com.aechak.application.review.admin.port.view.AdminReviewView;
import com.aechak.domain.review.review.Review;
import com.aechak.domain.review.review.enums.ReviewBlindActor;
import com.aechak.domain.review.review.enums.ReviewBlindReason;
import com.aechak.domain.review.review.enums.ReviewStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 상세. 신고 이력은 신고 접수 경로가 생기기 전까지 빈 목록이다 — 없다는 것과 아직 경로가 없다는 것을
 * 응답에서 구분하지 않는다(둘 다 "이 리뷰에 쌓인 신고가 없다"로 읽힌다).
 */
public record AdminReviewDetailResult(
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
        String optionName,
        ReviewStatus reviewStatus,
        ReviewBlindActor blindActor,
        ReviewBlindReason blindReasonCode,
        String blindReasonText,
        LocalDateTime blindedAt,
        Long blindedByAdminId,
        Long deletedByAdminId,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        List<AdminReviewImageResult> images,
        List<AdminReviewReportResult> reports) {

    public static AdminReviewDetailResult from(
            AdminReviewView view,
            String authorNickname,
            boolean authorWithdrawn,
            List<AdminReviewImageResult> images,
            List<AdminReviewReportResult> reports) {
        return new AdminReviewDetailResult(
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
                view.optionNameSnapshot(),
                view.reviewStatus(),
                view.blindActor(),
                view.blindReasonCode(),
                view.blindReasonText(),
                view.blindedAt(),
                view.blindedByAdminId(),
                view.deletedByAdminId(),
                view.deletedAt(),
                view.createdAt(),
                images,
                reports);
    }
}
