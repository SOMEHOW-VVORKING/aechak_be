package com.aechak.seller.product.response

import com.aechak.application.product.product.usecase.result.SellerProductOptionsResult

data class SellerProductOptionsResponse(
    val optionCombinations: List<OptionCombinationResponse>,
) {
    data class OptionCombinationResponse(
        val optionCombinationId: Long,
        val name: String,
        val additionalPrice: Long,
        val stockQuantity: Int,
        val isActive: Boolean,
    )

    companion object {
        fun from(result: SellerProductOptionsResult): SellerProductOptionsResponse =
            SellerProductOptionsResponse(
                optionCombinations =
                    result.optionCombinations.map {
                        OptionCombinationResponse(
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
