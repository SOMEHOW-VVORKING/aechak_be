package com.aechak.application.inspection.usecase.result;

import com.aechak.application.inspection.port.view.AdminInspectionDetailView;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionProcessingState;
import com.aechak.domain.inspection.enums.InspectionTargetType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 검수 한 건의 진행 현황 상세.
 *
 * <p>{@code perspectives}는 관점마다 한 칸이고 기록이 없는 관점은 칸 자체가 없다. 칸이 없는 것과
 * {@code UNCHECKED}는 다르다 — 앞은 파이프라인이 그 관점까지 가지 못한 것이고, 뒤는 거기까지는
 * 갔지만 보지 못한 것이다. 없는 칸을 통과로 채우지 않는다.
 *
 * <p>사유 셋({@code reasonCode}·{@code reasonSummary}·{@code sellerNotice})은 <b>이 검수 행에
 * 적힌 값</b>이다. 검토 큐 상세가 "지금 그 상품을 막고 있는 판정"에서 읽는 것과 다르다 — 여기서
 * 묻는 것은 상품의 현재 상태가 아니라 이 검수 한 건이 무엇을 했는지다.
 */
public record AdminInspectionDetailResult(
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
        String policyVersion,
        String promptVersion,
        String contentHash,
        String reasonCode,
        String reasonSummary,
        String sellerNotice,
        String leaseOwner,
        LocalDateTime leaseExpiresAt,
        List<InspectionPerspectiveResult> perspectives) {

    public static AdminInspectionDetailResult of(
            AdminInspectionDetailView view, List<InspectionPerspectiveResult> perspectives) {
        return new AdminInspectionDetailResult(
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
                view.policyVersion(),
                view.promptVersion(),
                view.contentHash(),
                view.reasonCode(),
                view.reasonSummary(),
                view.sellerNotice(),
                view.leaseOwner(),
                view.leaseExpiresAt(),
                perspectives);
    }
}
