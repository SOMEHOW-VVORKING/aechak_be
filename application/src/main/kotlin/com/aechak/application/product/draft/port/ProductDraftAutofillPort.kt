package com.aechak.application.product.draft.port

/**
 * 셀러 폼 초안 생성 포트. 구현은 infra/client/ai-client 다.
 *
 * 외부 실패를 예외로 던지지 않고 결과(UNAVAILABLE)로 돌린다 — 초안은 보조 기능이라 그 실패가
 * 셀러의 등록을 막아서는 안 된다. 어댑터가 403·409·503·422·타임아웃을 전부 그 값으로 옮긴다.
 */
interface ProductDraftAutofillPort {
    fun generate(request: ProductDraftAutofillRequest): ProductDraftAutofillResult
}

/**
 * 셀러센터 -> AI. 이미지와 셀러 식별자는 싣지 않는다 — AI 가 둘 다 읽지 않는다.
 * requestId 는 우리가 만들어 로그 상관 키로 쓴다.
 */
data class ProductDraftAutofillRequest(
    val requestId: String,
    val productName: String,
    val categoryId: Long?,
    val lockedFields: LockedFields?,
) {
    /** 재생성에서 셀러가 고쳐 지켜야 하는 값. 필드 이름은 AI 것을 그대로 쓴다. */
    data class LockedFields(
        val description: String?,
        val optionGroups: List<OptionGroup>?,
    )

    data class OptionGroup(
        val name: String,
        val values: List<String>,
    )
}
