package com.aechak.message.inspection

/** 검수 대상 종류. 상품은 노출 전 관문이고 리뷰는 사후 제재라 정책이 다르다 */
enum class InspectionTargetType {
    PRODUCT,
    REVIEW,
}

/**
 * 위반 분류. 상품과 리뷰가 같은 사전을 쓰되 쓰이는 부분집합이 다르다.
 *
 * 분류마다 판정 주체가 정해져 있고, 반려로 이어질 수 있는 것은 결정론적 규칙이 보는 분류뿐이다.
 * 모델이 보는 분류는 보류까지만 만들 수 있다.
 */
enum class ViolationCategory {
    /** 전화·카카오·계좌·외부 URL. 정규식이 판정하므로 즉시 반려까지 갈 수 있다 */
    CONTACT_INFO,

    /** 외부 거래 유도 */
    OFF_PLATFORM,

    /** 치료·완치 효능을 단정하는 표현 */
    MEDICAL_CLAIM,

    /** 근거 없는 효과 과장 */
    EFFICACY_OVERCLAIM,

    /**
     * 반려동물에게 위험한 성분.
     * 네거티브 리스트 매칭이 결정론적이라 모델 관점이 아니라 규칙 단계가 본다.
     */
    BANNED_INGREDIENT,

    /** 허위 원산지·인증·자격 */
    FALSE_CLAIM,

    /** 타사·타인 비방과 부당 비교 */
    DISPARAGEMENT,

    /** 욕설·혐오 */
    ABUSE,

    /** 광고·도배 */
    SPAM,

    /** 타인의 개인정보 */
    PII,

    /** 상품과 무관한 내용. 리뷰에만 쓴다 */
    IRRELEVANT,
}

/**
 * 판정 관점.
 *
 * 분류 개수로 쪼개지 않고 **참조하는 자료와 판단 양식**으로 쪼갠다. 라벨 이름만 다른
 * 근친 복제 프롬프트를 여러 개 두면 실패 지점과 꼬리 지연만 늘어난다.
 * 표시·광고는 정책 전문을 읽어 의미를 판단하고, 행위·거래는 구조와 행위를 본다.
 *
 * 성분 안전은 관점에서 뺐다. 금지 성분 매칭이 결정론적이라 모델에 맡길 근거가 없다.
 */
enum class InspectionPerspective {
    /** 관점을 쪼개지 않은 단일 판정. 팬아웃을 끄면 이 값으로 온다 */
    ALL,

    /** 표시·광고. 효능 주장, 허위·과장, 부당 비교 */
    DISPLAY_ADS,

    /** 행위·거래. 외부 거래 유도, 연락처·개인정보, 스팸, 욕설 */
    CONDUCT_TRADE,

    /** 결정론적 규칙 단계. 모델이 아니라 정규식과 네거티브 리스트가 낸 결과 */
    RULE,

    /** 선별 단계. 모델 판정 관점이 아니므로 "모델이 봤는가" 집계에서 RULE과 함께 제외한다 */
    TRIAGE,
}

/**
 * 관점별 결과.
 *
 * 통과와 미검사를 반드시 구분한다. 둘을 뭉개면 모델이 레이트 리밋으로 보지 못한 것이
 * "문제 없음"으로 기록되고, 그 상품이 검수를 통과한 것처럼 노출된다.
 */
enum class PerspectiveOutcome {
    VIOLATION,
    CLEAR,
    UNCHECKED,
}

/**
 * 최종 판정. 모델은 PASS와 FLAG만 낼 수 있고 이 열거에 반려가 없다.
 *
 * 판정 대상이 곧 적대적 사용자 입력이므로, 인젝션이 성공해도 통과 방향으로만 실패하게 둔다.
 * FAILED는 판정을 내리지 못한 것이고 통과가 아니다.
 */
enum class InspectionVerdict {
    PASS,
    FLAG,
    FAILED,
}
