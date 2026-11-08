package com.aechak.application.inspection.port.view;

import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionProcessingState;
import com.aechak.domain.inspection.enums.InspectionTargetType;
import java.time.LocalDateTime;

/**
 * 검수 한 건의 진행 상태 — 목록 한 행.
 *
 * @param judgment 판정. null은 아직 판정을 받지 못했다는 뜻이고 통과가 아니다
 * @param productPublicId 대상이 상품일 때의 공개 ULID. 대상이 리뷰면 null
 * @param productName 대상이 상품일 때의 상품명. 상품 행이 지워졌거나 대상이 리뷰면 null
 * @param settledAt 더 기다리지 않기로 한 시각. 진행 중이면 null
 */
public record AdminInspectionRowView(
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
        String policyVersion) {}
