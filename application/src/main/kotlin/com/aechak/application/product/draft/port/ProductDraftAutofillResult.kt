package com.aechak.application.product.draft.port

/**
 * AI -> 셀러센터.
 *
 * ``fieldStatus``·``basis``를 **문자열 지도로 둔다.** AI 쪽 열거(FieldStatus 16값)를 BE 열거로
 * 거울질하면 AI가 값을 하나 더할 때 역직렬화가 터져 셀러가 500을 본다. 그 값들은 화면이 분기에
 * 쓰는 축이고 BE는 판단하지 않으므로 그대로 통과시킨다.
 *
 * 둘을 함께 싣는 이유가 계약의 요점이다 — ``EMPTY_CALL_FAILED``(호출이 죽어서 못 봤다)와
 * ``EMPTY_MODEL_DECLINED``(모델이 제안하지 않았다)는 둘 다 빈 필드지만 뜻이 정반대이고,
 * 화면이 "다시 시도"와 "직접 작성"으로 갈라져야 한다.
 */
data class ProductDraftAutofillResult(
    val state: State,
    val draft: Draft,
    val fieldStatus: Map<String, String>,
    val basis: Map<String, String>,
    val sellerNotice: String?,
) {
    /**
     * OK·FAILED는 AI가 적은 값이고 UNAVAILABLE은 **우리가 더한 값**이다.
     *
     * **OK가 "필드를 받았다"를 뜻하지 않는다** — AI는 상품명 규칙 탈락·효능 단정·품목 미식별·
     * 모델 부재 같은 건너뜀도 state=OK로 돌리고 사유를 fieldStatus·sellerNotice에 적는다.
     */
    enum class State { OK, FAILED, UNAVAILABLE }

    /**
     * AI가 실제로 채우는 네 필드만 든다. 나머지 여덟(상품명·가격 둘·할인기간 둘·이미지 키 셋)은
     * AI가 ``_NEVER_ATTEMPTED``로 **항상 null**로 못박았고, 비어 있는 사유는 fieldStatus가
     * EMPTY_NOT_ATTEMPTED로 말한다.
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
