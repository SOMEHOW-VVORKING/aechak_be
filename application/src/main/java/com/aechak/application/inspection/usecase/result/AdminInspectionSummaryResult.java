package com.aechak.application.inspection.usecase.result;

import com.aechak.application.inspection.port.view.AdminInspectionRowView;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionProcessingState;
import com.aechak.domain.inspection.enums.InspectionTargetType;
import java.time.LocalDateTime;

/**
 * 검수 진행 현황 한 행.
 *
 * <p>{@code judgment}가 null인 것과 {@code PASS}는 다르다. null은 아직 판정이 없다는 뜻이고,
 * {@code UNJUDGED}(기한 초과)와 {@code FAILED}(처리 실패)도 통과가 아니다. 셋을 한 묶음으로
 * 묶어 내려보내지 않는다 — 운영자가 검수를 마친 건으로 읽는다.
 */
public record AdminInspectionSummaryResult(
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

    public static AdminInspectionSummaryResult from(AdminInspectionRowView view) {
        return new AdminInspectionSummaryResult(
                view.inspectionId(),
                view.targetType(),
                view.targetRef(),
                view.productPublicId(),
                view.productName(),
                view.processingState(),
                view.judgment(),
                view.attempt(),
                view.requestedAt(),
                view.settledAt(),
                view.policyVersion());
    }
}
