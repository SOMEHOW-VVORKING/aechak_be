package com.aechak.application.inspection.port;

import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionProcessingState;
import com.aechak.domain.inspection.enums.InspectionTargetType;

/**
 * 검수 진행 현황 목록 조건의 영속 계층 어휘.
 *
 * @param processingState 처리 상태. null이면 조건 없음
 * @param targetType 대상 종류. null이면 조건 없음
 * @param judgment 판정. null이면 조건 없음 — 판정이 아직 없는 행까지 함께 본다는 뜻이다
 */
public record AdminInspectionPageCondition(
        InspectionProcessingState processingState,
        InspectionTargetType targetType,
        InspectionJudgment judgment,
        long offset,
        int limit) {}
