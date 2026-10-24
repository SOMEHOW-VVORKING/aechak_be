package com.aechak.admin.product.response;

import com.aechak.application.inspection.usecase.result.AdminProductInspectionSummaryResult;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.ReviewQueueStatus;
import java.time.LocalDateTime;

/** latestJudgment가 null이면 셀러가 고친 내용을 AI가 아직 보는 중임 */
public record AdminProductInspectionSummaryResponse(
        String productId,
        String productName,
        String sellerName,
        ReviewQueueStatus status,
        InspectionJudgment latestJudgment,
        LocalDateTime blindedAt) {

    public static AdminProductInspectionSummaryResponse from(AdminProductInspectionSummaryResult result) {
        return new AdminProductInspectionSummaryResponse(
                result.productId(),
                result.productName(),
                result.sellerName(),
                result.status(),
                result.latestJudgment(),
                result.blindedAt());
    }
}
