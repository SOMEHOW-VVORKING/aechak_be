package com.aechak.application.product.draft.port

/**
 * AI -> 셀러센터.
 *
 * fieldStatus(왜 비었는가)·basis(무슨 근거로 채웠는가)는 문자열 지도로 둔다. AI 쪽 열거를 BE
 * 열거로 거울질하면 AI 가 값을 하나 더할 때 역직렬화가 터져 셀러가 500 을 본다.
 */
data class ProductDraftAutofillResult(
    val state: State,
    val draft: Draft,
    val fieldStatus: Map<String, String>,
    val basis: Map<String, String>,
    val sellerNotice: String?,
) {
    /**
     * OK·FAILED 는 AI 가 적은 값이고 UNAVAILABLE 은 우리가 더한 값이다.
     * OK 가 "필드를 받았다"를 뜻하지 않는다 — AI 는 건너뜀도 OK 로 돌리고 사유만 적는다.
     */
    enum class State { OK, FAILED, UNAVAILABLE }

    /**
     * AI 가 실제로 채우는 네 필드만 든다. 나머지 여덟(상품명·가격·할인기간·이미지 키)은 AI 가
     * 설계상 항상 null 로 두고, 비어 있는 사유는 fieldStatus 가 말한다.
     */
    data class Draft(
        val categoryId: Long? = null,
        val description: String? = null,
        val optionGroups: List<OptionGroup> = emptyList(),
        val optionCombinations: List<OptionCombination> = emptyList(),
    )

    data class OptionGroup(
        val name: String?,
        val values: List<String>,
    )

    data class OptionCombination(
        val optionValues: List<String>,
        val additionalPrice: Long?,
        val stockQuantity: Int?,
    )

    companion object {
        /** 어댑터 부재·AI 장애·설정 어긋남. 폼을 빈 상태로 그대로 둔다. */
        fun unavailable(): ProductDraftAutofillResult =
            ProductDraftAutofillResult(
                state = State.UNAVAILABLE,
                draft = Draft(),
                fieldStatus = emptyMap(),
                basis = emptyMap(),
                sellerNotice = null,
            )
    }
}
