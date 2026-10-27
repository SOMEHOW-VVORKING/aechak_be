package com.aechak.domain.inspection.enums

/**
 * 검수 대상 종류.
 *
 * 계약(message) 쪽에 같은 이름의 열거가 따로 있다. 도메인이 message를 의존하지 않는 규칙 때문이고,
 * 둘이 갈라질 수 있는 것은 의도된 것이다 — 계약 버전은 외부와의 약속이고 이쪽은 우리 저장 모델이다.
 */
enum class InspectionTargetType {
    PRODUCT,
    REVIEW,
}

/**
 * 처리 상태. 판정 값과 분리해 둔다.
 *
 * 하나로 묶으면 보류·실패 판정이 난 행이 타임아웃 조회에서 빠져 영구 보류가 된다.
 * 타임아웃 승격은 "판정을 아직 못 받은 건"만 대상이어야 한다.
 */
enum class InspectionProcessingState {
    /** 요청을 적었고 아직 보내지 않았거나 재시도를 기다린다 */
    QUEUED,

    /** 발행했고 판정을 기다린다 */
    IN_FLIGHT,

    /** 더 기다리지 않는다. 판정이 왔거나 포기했다 */
    SETTLED,
}

/** 판정 값. 모델은 통과와 보류만 낼 수 있고 반려는 여기 없다 */
enum class InspectionJudgment {
    PASS,
    FLAG,

    /** 판정을 받지 못한 채 기한이 지났다. 통과가 아니다 */
    UNJUDGED,

    /** 판정 과정이 실패했다 */
    FAILED,
}

/** 관점. 결정론적 규칙 단계도 한 줄로 기록해 "봤다"를 남긴다 */
enum class InspectionPerspectiveKind {
    ALL,
    DISPLAY_ADS,
    CONDUCT_TRADE,
    RULE,
}

/** 관점별 결과. 통과와 미검사를 반드시 구분한다 */
enum class PerspectiveOutcome {
    VIOLATION,
    CLEAR,
    UNCHECKED,
}
