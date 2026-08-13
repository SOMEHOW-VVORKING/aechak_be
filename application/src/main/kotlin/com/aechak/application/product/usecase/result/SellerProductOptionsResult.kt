package com.aechak.application.product.usecase.result

import com.aechak.application.product.port.view.SellerProductOptionView

/** 셀러 상품 옵션 재고 결과 — 비활성 조합 포함, 구매자 응답과 달리 재고 원값을 그대로 노출 */
data class SellerProductOptionsResult(
    val optionCombinations: List<OptionCombinationResult>,
) {
    data class OptionCombinationResult(
        val optionCombinationId: Long,
        val name: String,
        val additionalPrice: Long,
        val stockQuantity: Int,
        val isActive: Boolean,
    )

    companion object {
        fun from(views: List<SellerProductOptionView>): SellerProductOptionsResult =
            SellerProductOptionsResult(
                optionCombinations =
                    views.map {
                        OptionCombinationResult(
                            optionCombinationId = it.optionCombinationId,
                            name = it.name,
                            additionalPrice = it.additionalPrice,
                            stockQuantity = it.stockQuantity,
                            isActive = it.isActive,
                        )
                    },
            )
    }
}
