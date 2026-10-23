package com.aechak.application.inspection.usecase.result;

import com.aechak.application.inspection.port.view.ReviewTargetProductView;
import com.aechak.application.inspection.service.InspectionSnapshot;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionProcessingState;
import com.aechak.domain.product.product.enums.InspectionStatus;
import java.time.LocalDateTime;
import java.util.List;

/** 검수 필드는 상품이 한 번도 검수를 타지 않았으면 전부 null임 */
public record AdminProductInspectionDetailResult(
        String productId,
        String productName,
        String sellerName,
        List<String> categoryNames,
        String description,
        String representativeImageUrl,
        List<String> optionNames,
        InspectionStatus inspectionStatus,
        boolean awaitingAdminReview,
        InspectionProcessingState processingState,
        InspectionJudgment judgment,
        String reasonCode,
        String reasonSummary,
        String sellerNotice,
        LocalDateTime requestedAt,
        LocalDateTime settledAt,
        List<ProductRejectionResult> rejections) {

    public static AdminProductInspectionDetailResult of(
            ReviewTargetProductView product,
            String representativeImageUrl,
            InspectionSnapshot snapshot,
            boolean awaitingAdminReview,
            List<ProductRejectionResult> rejections) {
        return new AdminProductInspectionDetailResult(
                product.getProductPublicId(),
                product.getProductName(),
                product.getSellerName(),
                product.getCategoryNames(),
                product.getDescription(),
                representativeImageUrl,
                product.getOptionNames(),
                product.getInspectionStatus(),
                awaitingAdminReview,
                snapshot == null ? null : snapshot.getProcessingState(),
                snapshot == null ? null : snapshot.getJudgment(),
                snapshot == null ? null : snapshot.getReasonCode(),
                snapshot == null ? null : snapshot.getReasonSummary(),
                snapshot == null ? null : snapshot.getSellerNotice(),
                snapshot == null ? null : snapshot.getRequestedAt(),
                snapshot == null ? null : snapshot.getSettledAt(),
                rejections);
    }
}
