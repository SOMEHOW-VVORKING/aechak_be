package com.aechak.admin.inspection.response;

import com.aechak.application.inspection.usecase.result.AdminInspectionDetailResult;
import com.aechak.application.inspection.usecase.result.InspectionPerspectiveResult;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind;
import com.aechak.domain.inspection.enums.InspectionProcessingState;
import com.aechak.domain.inspection.enums.InspectionTargetType;
import com.aechak.domain.inspection.enums.PerspectiveOutcome;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 검수 한 건의 진행 현황 상세.
 *
 * <p>{@code perspectives}는 관점마다 한 칸이고 파이프라인 순서(RULE·TRIAGE·DISPLAY_ADS·
 * CONDUCT_TRADE·ALL)로 온다. <b>칸이 없는 것과 {@code UNCHECKED}는 다르다</b> — 앞은 그 관점까지
 * 가지 못한 것이고 뒤는 거기까지는 갔지만 보지 못한 것이다. 어느 쪽도 통과로 그리지 않는다.
 *
 * <p>사유 셋({@code reasonCode}·{@code reasonSummary}·{@code sellerNotice})은 이 검수 행에 적힌
 * 값이다. 검수 큐 상세({@code /admin/product-inspections/{productId}})가 "지금 그 상품을 막고 있는
 * 판정"에서 읽는 것과 다르다.
 *
 * <p>임차 둘({@code leaseOwner}·{@code leaseExpiresAt})은 지금 이 건을 잡고 있는 워커와 그 점유가
 * 풀리는 시각이다. 멈춘 건을 진단하는 유일한 단서라 싣는다 — 운영자 전용 경로이고 담기는 값은
 * 워커 인스턴스 표식이지 사람에 관한 정보가 아니다. 목록에는 싣지 않는다(한 번에 100행까지
 * 내보내는 자리라 내부 표식의 노출면이 행 수만큼 커지는데, 진단에 필요한 것은 그 한 건의 소유자뿐이다).
 */
public record AdminInspectionDetailResponse(
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
        List<Perspective> perspectives) {

    public static AdminInspectionDetailResponse from(AdminInspectionDetailResult result) {
        return new AdminInspectionDetailResponse(
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
                result.policyVersion(),
                result.promptVersion(),
                result.contentHash(),
                result.reasonCode(),
                result.reasonSummary(),
                result.sellerNotice(),
                result.leaseOwner(),
                result.leaseExpiresAt(),
                result.perspectives().stream().map(Perspective::from).toList());
    }

    /**
     * 관점 하나의 결과.
     *
     * @param skipReason 미검사인 이유. 본 관점에는 null이다
     * @param violations 위반 상세 JSON 원문. 위반이 아니면 null
     */
    public record Perspective(
            InspectionPerspectiveKind perspective, PerspectiveOutcome outcome, String skipReason, String violations) {

        static Perspective from(InspectionPerspectiveResult result) {
            return new Perspective(result.perspective(), result.outcome(), result.skipReason(), result.violations());
        }
    }
}
