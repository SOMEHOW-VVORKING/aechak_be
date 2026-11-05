package com.aechak.seller.product.response

import com.aechak.application.product.draft.port.ProductDraftAutofillResult
import com.aechak.seller.product.support.ProductDraftPayload

/**
 * 폼 초안 응답. 화면이 빈 필드를 설명하는 축은 state 가 아니라 fieldStatus·sellerNotice 다 —
 * AI 는 건너뜀도 OK 로 돌린다.
 *
 * 운영 축(usage·guardDrops·itemSignal·이미지 보고)은 싣지 않는다. 셀러가 쓸 수 없는 값이고
 * 모델 비용은 우리 로그에만 남긴다.
 */
data class ProductDraftAutofillResponse(
    val state: String,
    val draft: AutofillDraft,
    val fieldStatus: Map<String, String>,
    val basis: Map<String, String>,
    val sellerNotice: String?,
) {
    /**
     * AI 가 채우는 네 필드만 든다. 옵션 모양은 임시저장 페이로드 타입을 그대로 써서, 셀러가
     * 받아들인 값이 임시저장·등록 요청으로 그대로 되돌아가게 한다.
     */
    data class AutofillDraft(
        val categoryId: Long?,
        val description: String?,
        val optionGroups: List<ProductDraftPayload.OptionGroupItem>,
        val optionCombinations: List<ProductDraftPayload.OptionCombinationItem>,
    )

    companion object {
        fun from(result: ProductDraftAutofillResult): ProductDraftAutofillResponse =
            ProductDraftAutofillResponse(
                state = result.state.name,
                draft =
                    AutofillDraft(
                        categoryId = result.draft.categoryId,
                        description = result.draft.description,
                        optionGroups =
                            result.draft.optionGroups.map {
                                ProductDraftPayload.OptionGroupItem(it.name, it.values)
                            },
                        optionCombinations =
                            result.draft.optionCombinations.map {
                                ProductDraftPayload.OptionCombinationItem(it.optionValues, it.additionalPrice, it.stockQuantity)
                            },
                    ),
                fieldStatus = result.fieldStatus,
                basis = result.basis,
                sellerNotice = result.sellerNotice,
            )
    }
}
