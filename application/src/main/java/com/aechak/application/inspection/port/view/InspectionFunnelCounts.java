package com.aechak.application.inspection.port.view;

/**
 * 단계별 탈출 분포의 원재료 — 검수 한 건이 어디서 끝났는지를 버킷으로 가른 건수.
 *
 * <p>터미널을 <b>여덟</b>으로 못박는다. 파이프라인이 말하는 탈출 다섯에 판정 실패와 미판정,
 * 그리고 미분류가 더해진 것이다. 다섯만 세면 합이 설정 건수에 못 미쳐 "몇 퍼센트가 어디서
 * 끝나는가"라는 질문의 분모가 깨진다. {@code unclassifiedCount}는 평소 0이고, 0이 아니면
 * 그 자체가 사건이다 — 새 터미널이 생겼거나 분류 규칙이 실제 기록과 어긋났다는 뜻이다.
 *
 * <p>분류는 {@code judgment}와 관점 기록의 플래그로만 한다. 계약 필드 {@code criticOverturned}로
 * 뒤집힘을 가르지 않는다 — 그 필드는 저장되지 않고, 통과 판정에 모델 위반 관점 행이 남는 길은
 * 뒤집힘뿐이라 플래그만으로 복원된다.
 *
 * <p>{@code modelViolationCount}는 4단의 분모이자 3단의 분자다. 비평이 뒤집어도 모델 위반
 * 관점 행은 남으므로 "모델이 찾아낸 비율"이 뒤집힘에 지워지지 않는다.
 *
 * <p>{@code modelDispatchedCount}는 <b>모델 호출이 실제로 나간</b> 건이다. 레이트 리밋으로 못 본
 * 건이 여기 들어오고 {@code modelViolationCount + modelClearCount}에는 들어오지 않는다 — 그
 * 차이가 가용성 때문에 못 본 양이다. 선별이 건너뛴 건과 스텁 모드(LLM_DISABLED) 건과 기한 만료
 * 표식은 여기 들어오지 않는다. 호출이 한 번도 안 나간 건을 "모델 단계에 들어갔다"로 세면 3단
 * 검출율이 가용성이 아니라 조립 상태를 재게 된다.
 *
 * <p>{@code expiredMarkerCount}는 기한 정리가 남긴 미검사 표식({@code WAIT_LIMIT_EXCEEDED})이 있는
 * 검수 수다. <b>{@code unjudgedCount}와 같아야 한다.</b> 어긋나면 사건이다 — 다른 경로가 미판정을
 * 만들었거나, 기한 스윕이 검수 행만 닫고 관점 행을 못 적었다는 뜻이다. 이 한 줄이 "만료 건을
 * 표식으로 명시 제외한다"는 규약의 전제를 운영 중에 감시한다.
 *
 * <p>{@code missingSkipReasonCount}는 사유 없는 미검사 행이 있는 검수 수다. 0이어야 한다 —
 * 선별이 일부러 건너뛴 것과 호출이 죽어 못 본 것을 가르는 축이 사유 하나뿐이라, 사유가 비면
 * 그 건은 어느 쪽으로도 셀 수 없다.
 */
public record InspectionFunnelCounts(
        long settledCount,
        long passCount,
        long flagCount,
        long unjudgedCount,
        long failedCount,
        long ruleBlockedCount,
        long triageClearedCount,
        long modelClearExitCount,
        long criticOverturnedCount,
        long modelFlagCount,
        long verdictFailedExitCount,
        long unjudgedExitCount,
        long unclassifiedCount,
        long modelViolationCount,
        long modelClearCount,
        long modelDispatchedCount,
        long ruleHitBypassCount,
        long expiredMarkerCount,
        long missingSkipReasonCount) {

    public static InspectionFunnelCounts empty() {
        return new InspectionFunnelCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
