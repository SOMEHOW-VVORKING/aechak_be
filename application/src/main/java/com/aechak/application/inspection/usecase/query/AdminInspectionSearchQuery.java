package com.aechak.application.inspection.usecase.query;

import com.aechak.application.inspection.port.AdminInspectionPageCondition;
import com.aechak.application.support.PageQuery;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionProcessingState;
import com.aechak.domain.inspection.enums.InspectionTargetType;

/**
 * 검수 진행 현황 목록 조회 조건. 세 축은 모두 선택이고 주지 않으면 전체다.
 *
 * <p>"판정을 아직 못 받은 건"은 판정 축으로 고를 수 없다. 그 상태는 판정이 비어 있는 것이고
 * {@link InspectionJudgment}에는 그 값이 없다 — 처리 상태 축({@code QUEUED}·{@code IN_FLIGHT})으로
 * 고른다. 두 칼럼이 갈라져 있는 이유가 그것이라, 판정에 "없음" 값을 만들어 두 축을 합치지 않는다.
 *
 * @param processingState 처리 상태. null이면 전체
 * @param targetType 대상 종류. null이면 전체
 * @param judgment 판정. null이면 전체(판정이 없는 행도 함께 본다)
 */
public record AdminInspectionSearchQuery(
        InspectionProcessingState processingState,
        InspectionTargetType targetType,
        InspectionJudgment judgment,
        int page,
        int size) {

    /** page·size의 형식 오류는 PageQuery가 보정한다(음수 page→0, size는 1~100 절삭). */
    public static AdminInspectionSearchQuery of(
            InspectionProcessingState processingState,
            InspectionTargetType targetType,
            InspectionJudgment judgment,
            int page,
            int size) {
        PageQuery pageQuery = PageQuery.of(page, size);
        return new AdminInspectionSearchQuery(
                processingState, targetType, judgment, pageQuery.getPage(), pageQuery.getSize());
    }

    public AdminInspectionPageCondition toCondition() {
        return new AdminInspectionPageCondition(processingState, targetType, judgment, (long) page * size, size);
    }
}
