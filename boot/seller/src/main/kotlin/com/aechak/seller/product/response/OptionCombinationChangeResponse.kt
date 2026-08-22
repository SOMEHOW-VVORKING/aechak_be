package com.aechak.seller.product.response

import com.aechak.application.product.product.usecase.result.OptionCombinationChangeResult
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class OptionCombinationChangeResponse(
    val combinationId: Long,
    val stockQuantity: Int,
    /** 요청한 stockDelta 중 실제로 반영된 양. 잔량이 모자라 0에서 멈추면 요청보다 작음. */
    val appliedStockDelta: Int,
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(result: OptionCombinationChangeResult): OptionCombinationChangeResponse =
            OptionCombinationChangeResponse(
                combinationId = result.combinationId,
                stockQuantity = result.stockQuantity,
                appliedStockDelta = result.appliedStockDelta,
                isActive = result.isActive,
                updatedAt = result.updatedAt,
            )
    }
}
