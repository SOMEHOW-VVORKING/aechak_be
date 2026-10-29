package com.aechak.admin.review.response;

import com.aechak.application.review.admin.usecase.result.AdminReviewDetailResult;
import com.aechak.domain.review.review.enums.ReviewBlindActor;
import com.aechak.domain.review.review.enums.ReviewBlindReason;
import com.aechak.domain.review.review.enums.ReviewStatus;
import java.time.LocalDateTime;
import java.util.List;

public record AdminReviewDetailResponse(
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
        List<AdminReviewImageResponse> images,
        List<AdminReviewReportResponse> reports) {

    public static AdminReviewDetailResponse from(AdminReviewDetailResult result) {
        return new AdminReviewDetailResponse(
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
                result.optionName(),
                result.reviewStatus(),
                result.blindActor(),
                result.blindReasonCode(),
                result.blindReasonText(),
                result.blindedAt(),
                result.blindedByAdminId(),
                result.deletedByAdminId(),
                result.deletedAt(),
                result.createdAt(),
                result.images().stream().map(AdminReviewImageResponse::from).toList(),
                result.reports().stream().map(AdminReviewReportResponse::from).toList());
    }
}
