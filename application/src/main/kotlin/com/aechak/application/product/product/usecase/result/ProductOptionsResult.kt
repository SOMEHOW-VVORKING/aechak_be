package com.aechak.application.product.product.usecase.result

import com.aechak.application.product.product.port.view.ProductOptionsView
import com.aechak.domain.product.option.OptionCombination

/** 공개 상품 옵션 결과 */
data class ProductOptionsResult(
    val optionGroups: List<OptionGroupResult>,
    val optionCombinations: List<OptionCombinationResult>,
) {
    data class OptionGroupResult(
        val optionGroupId: Long,
        val name: String,
        val sortOrder: Int,
        val values: List<OptionValueResult>,
    )

    data class OptionValueResult(
        val optionValueId: Long,
        val name: String,
        val sortOrder: Int,
    )

    data class OptionCombinationResult(
        val optionCombinationId: Long,
        val name: String,
        val additionalPrice: Long,
        /** 구성 옵션 값 id 오름차순 — 클라이언트가 그룹별 선택값으로 조합을 매칭한다. 항상 그룹 목록에 있는 id만 담는다. */
        val optionValueIds: List<Long>,
        val remainingStock: Int?, // 재고 임계치 이하인 경우에만 포함, 그 외에는 null
        val soldOut: Boolean,
    )

    companion object {
        fun from(view: ProductOptionsView): ProductOptionsResult =
            ProductOptionsResult(
                optionGroups =
                    view.optionGroups.map { group ->
                        OptionGroupResult(
                            optionGroupId = group.optionGroupId,
                            name = group.name,
                            sortOrder = group.sortOrder,
                            values = group.values.map { OptionValueResult(it.optionValueId, it.name, it.sortOrder) },
                        )
                    },
                optionCombinations =
                    view.optionCombinations.map { combination ->
                        OptionCombinationResult(
                            optionCombinationId = combination.optionCombinationId,
                            name = combination.name,
                            additionalPrice = combination.additionalPrice,
                            optionValueIds = combination.optionValueIds,
                            remainingStock =
                                combination.stockQuantity.takeIf { it <= OptionCombination.STOCK_DISPLAY_THRESHOLD },
                            soldOut = combination.stockQuantity == 0,
                        )
                    },
            )
    }
}
