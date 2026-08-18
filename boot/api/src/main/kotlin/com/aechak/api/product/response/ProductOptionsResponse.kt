package com.aechak.api.product.response

import com.aechak.application.product.product.usecase.result.ProductOptionsResult

data class ProductOptionsResponse(
    val optionGroups: List<OptionGroupResponse>,
    val optionCombinations: List<OptionCombinationResponse>,
) {
    data class OptionGroupResponse(
        val optionGroupId: Long,
        val name: String,
        val sortOrder: Int,
        val values: List<OptionValueResponse>,
    )

    data class OptionValueResponse(
        val optionValueId: Long,
        val name: String,
        val sortOrder: Int,
    )

    data class OptionCombinationResponse(
        val optionCombinationId: Long,
        val name: String,
        val additionalPrice: Long,
        val optionValueIds: List<Long>,
        val remainingStock: Int?,
        val soldOut: Boolean,
    )

    companion object {
        fun from(result: ProductOptionsResult): ProductOptionsResponse =
            ProductOptionsResponse(
                optionGroups =
                    result.optionGroups.map { group ->
                        OptionGroupResponse(
                            optionGroupId = group.optionGroupId,
                            name = group.name,
                            sortOrder = group.sortOrder,
                            values = group.values.map { OptionValueResponse(it.optionValueId, it.name, it.sortOrder) },
                        )
                    },
                optionCombinations =
                    result.optionCombinations.map { combination ->
                        OptionCombinationResponse(
                            optionCombinationId = combination.optionCombinationId,
                            name = combination.name,
                            additionalPrice = combination.additionalPrice,
                            optionValueIds = combination.optionValueIds,
                            remainingStock = combination.remainingStock,
                            soldOut = combination.soldOut,
                        )
                    },
            )
    }
}
