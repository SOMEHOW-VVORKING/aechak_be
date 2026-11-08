package com.aechak.application.inspection.port.view;

import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionProcessingState;
import com.aechak.domain.inspection.enums.InspectionTargetType;
import java.time.LocalDateTime;

/**
 * 검수 한 건의 진행 상태 — 상세. 목록 행에 사유·버전·임차를 더한 것이다.
 *
 * @param judgment 판정. null은 아직 판정을 받지 못했다는 뜻이고 통과가 아니다
 * @param leaseOwner 지금 이 건을 잡고 있는 워커. 잡은 워커가 없으면 null
 * @param leaseExpiresAt 그 점유가 풀리는 시각. 지난 시각이면 다음 주기에 재발행 대상이다
 */
public record AdminInspectionDetailView(
        long inspectionId,
        InspectionTargetType targetType,
        long targetRef,
        String productPublicId,
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
        LocalDateTime leaseExpiresAt) {}
