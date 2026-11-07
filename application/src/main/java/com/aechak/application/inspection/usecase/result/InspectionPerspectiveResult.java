package com.aechak.application.inspection.usecase.result;

import com.aechak.domain.inspection.InspectionFinding;
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind;
import com.aechak.domain.inspection.enums.PerspectiveOutcome;

/**
 * 관점 하나의 결과.
 *
 * <p>{@code UNCHECKED}는 통과가 아니다 — 그 관점이 돌지 못했다는 뜻이고 이유는 {@code skipReason}이
 * 든다. 통과({@code CLEAR})와 한 값으로 합치면 모델이 보지 못한 것이 "문제 없음"으로 보인다.
 *
 * @param violations 위반 상세 JSON 원문. 위반이 아니면 null
 */
public record InspectionPerspectiveResult(
        InspectionPerspectiveKind perspective, PerspectiveOutcome outcome, String skipReason, String violations) {

    public static InspectionPerspectiveResult from(InspectionFinding finding) {
        return new InspectionPerspectiveResult(
                finding.getPerspective(), finding.getOutcome(), finding.getSkipReason(), finding.getViolations());
    }
}
