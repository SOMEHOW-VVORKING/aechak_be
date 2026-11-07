package com.aechak.admin.inspection.response;

import com.aechak.application.inspection.usecase.result.AdminInspectionSummaryResult;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionProcessingState;
import com.aechak.domain.inspection.enums.InspectionTargetType;
import java.time.LocalDateTime;

/**
 * 검수 진행 현황 한 행.
 *
 * <p>{@code judgment}가 null인 것은 <b>아직 판정이 없다</b>는 뜻이고 통과가 아니다. {@code UNJUDGED}
 * (기한 초과)와 {@code FAILED}(처리 실패)도 통과가 아니다 — 화면에서 그 셋을 PASS와 같은 칸에
 * 묶지 않는다.
 *
 * <p>{@code productId}·{@code productName}은 대상이 상품일 때만 온다. 리뷰 검수거나 상품 행이
 * 지워졌으면 둘 다 null이고, 그때도 {@code targetRef}로 어느 대상이었는지는 남는다.
 */
public record AdminInspectionSummaryResponse(
        long inspectionId,
        InspectionTargetType targetType,
        long targetRef,
        String productId,
        String productName,
        InspectionProcessingState processingState,
        InspectionJudgment judgment,
        int attempt,
        LocalDateTime requestedAt,
        LocalDateTime settledAt,
        String policyVersion) {

    public static AdminInspectionSummaryResponse from(AdminInspectionSummaryResult result) {
        return new AdminInspectionSummaryResponse(
                result.inspectionId(),
                result.targetType(),
                result.targetRef(),
                result.productId(),
                result.productName(),
                result.processingState(),
                result.judgment(),
                result.attempt(),
                result.requestedAt(),
                result.settledAt(),
                result.policyVersion());
    }
}
