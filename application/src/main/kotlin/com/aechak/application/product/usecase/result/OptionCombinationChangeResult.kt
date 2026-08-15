package com.aechak.application.product.usecase.result

import com.aechak.domain.product.option.OptionCombination
import java.time.LocalDateTime

data class OptionCombinationChangeResult(
    val combinationId: Long,
    val stockQuantity: Int,
    val appliedStockDelta: Int,
    val isActive: Boolean,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun of(
            combination: OptionCombination,
            appliedStockDelta: Int,
        ): OptionCombinationChangeResult =
            OptionCombinationChangeResult(
                combinationId = combination.id,
                stockQuantity = combination.stockQuantity,
                appliedStockDelta = appliedStockDelta,
                isActive = combination.isActive,
                updatedAt = combination.updatedAt,
            )
    }
}
