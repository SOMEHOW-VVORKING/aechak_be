package com.aechak.application.inspection.usecase.result;

import com.aechak.application.inspection.port.view.ProductReviewQueueRowView;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.ReviewQueueStatus;
import java.time.LocalDateTime;

public record AdminProductInspectionSummaryResult(
        String productId,
        String productName,
        String sellerName,
        ReviewQueueStatus status,
        InspectionJudgment latestJudgment,
        LocalDateTime blindedAt) {

    public static AdminProductInspectionSummaryResult from(ProductReviewQueueRowView view) {
        return new AdminProductInspectionSummaryResult(
                view.getProductPublicId(),
                view.getProductName(),
                view.getSellerName(),
                view.getStatus(),
                view.getLatestJudgment(),
                view.getBlindedAt());
    }
}
