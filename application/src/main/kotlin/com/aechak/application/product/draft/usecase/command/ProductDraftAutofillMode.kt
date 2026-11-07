package com.aechak.application.product.draft.usecase.command

/**
 * 초안을 어떻게 채우는가. 셀러센터가 정해 AI 로 넘긴다.
 *
 * 가드는 양쪽이 똑같이 지난다 -- 0단 규칙·금지 성분·효능 사전을 통과한 슬롯만 살아남는다.
 * 보강이 가드를 비켜 가면 초안이 만든 문구가 검수에서 걸려 셀러가 두 번 일한다.
 */
enum class ProductDraftAutofillMode {
    /** 셀러가 쓴 값은 건드리지 않고 빈 칸만 채운다. 생략했을 때의 값이다 */
    FILL,

    /** 셀러 원문을 모델 입력으로 주고 다시 쓴다 */
    REFINE,
}
