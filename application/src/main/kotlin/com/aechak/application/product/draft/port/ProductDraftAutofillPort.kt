package com.aechak.application.product.draft.port

import com.aechak.application.product.draft.usecase.command.ProductDraftAutofillMode

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
 * 셀러센터 -> AI. 이미지와 셀러 식별자는 싣지 않는다 — 초안이 채우는 값이 아니고 보내면
 * 주입 표면만 넓어진다. requestId 는 우리가 만들어 로그 상관 키로 쓴다.
 *
 * [mode] 는 AI 가 읽는다. FILL 은 빈 칸만, REFINE 은 [lockedFields] 원문까지 다시 쓴다.
 */
data class ProductDraftAutofillRequest(
    val requestId: String,
    val productName: String,
    val categoryId: Long?,
    val lockedFields: LockedFields?,
    val mode: ProductDraftAutofillMode,
) {
    /** 셀러가 고쳐 둔 값. FILL 에서는 지켜야 하는 값이고 REFINE 에서는 다시 쓸 입력이다. */
    data class LockedFields(
        val description: String?,
        val optionGroups: List<OptionGroup>?,
    )

    data class OptionGroup(
        val name: String,
        val values: List<String>,
    )
}
