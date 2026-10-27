package com.aechak.admin.product.response;

import com.aechak.application.inspection.usecase.result.AdminProductInspectionDetailResult;
import com.aechak.application.inspection.usecase.result.ProductRejectionResult;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionProcessingState;
import com.aechak.domain.product.product.enums.InspectionStatus;
import java.time.LocalDateTime;
import java.util.List;

/** 검수 필드(processingState부터 settledAt까지)는 상품이 한 번도 검수를 타지 않았으면 null임.
 * appealReason은 셀러가 적은 문장이고 열린 행에 이의가 걸렸을 때만 옴 */
public record AdminProductInspectionDetailResponse(
        String productId,
        String productName,
        String sellerName,
        List<String> categoryNames,
        String description,
        String representativeImageUrl,
        List<String> optionNames,
        InspectionStatus inspectionStatus,
        boolean awaitingAdminReview,
        LocalDateTime appealedAt,
        String appealReason,
        InspectionProcessingState processingState,
        InspectionJudgment judgment,
        String reasonCode,
        String reasonSummary,
        String sellerNotice,
        LocalDateTime requestedAt,
        LocalDateTime settledAt,
        List<Rejection> rejections) {

    public static AdminProductInspectionDetailResponse from(AdminProductInspectionDetailResult result) {
        return new AdminProductInspectionDetailResponse(
                result.productId(),
                result.productName(),
                result.sellerName(),
                result.categoryNames(),
                result.description(),
                result.representativeImageUrl(),
                result.optionNames(),
                result.inspectionStatus(),
                result.awaitingAdminReview(),
                result.appealedAt(),
                result.appealReason(),
                result.processingState(),
                result.judgment(),
                result.reasonCode(),
                result.reasonSummary(),
                result.sellerNotice(),
                result.requestedAt(),
                result.settledAt(),
                result.rejections().stream().map(Rejection::from).toList());
    }

    public record Rejection(String reason, LocalDateTime rejectedAt) {

        static Rejection from(ProductRejectionResult result) {
            return new Rejection(result.reason(), result.rejectedAt());
        }
    }
}
