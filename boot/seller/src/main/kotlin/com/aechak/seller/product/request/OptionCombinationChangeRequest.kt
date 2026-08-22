package com.aechak.seller.product.request

import com.aechak.application.product.product.usecase.command.ChangeOptionCombinationCommand
import jakarta.validation.constraints.AssertTrue

data class OptionCombinationChangeRequest(
    val stockDelta: Int? = null,
    val isActive: Boolean? = null,
) {
    @get:AssertTrue(message = "재고 증감과 판매 여부 중 하나는 보내야 합니다.")
    val changeRequired: Boolean
        get() = stockDelta != null || isActive != null

    @get:AssertTrue(message = "재고 증감은 0이 아닌 ±$STOCK_DELTA_LIMIT 이내여야 합니다.")
    val stockDeltaWellFormed: Boolean
        get() = stockDelta == null || (stockDelta != 0 && stockDelta in -STOCK_DELTA_LIMIT..STOCK_DELTA_LIMIT)

    fun toCommand(
        sellerId: Long,
        productPublicId: String,
        combinationId: Long,
    ): ChangeOptionCombinationCommand =
        ChangeOptionCombinationCommand(
            sellerId = sellerId,
            productPublicId = productPublicId,
            combinationId = combinationId,
            stockDelta = stockDelta,
            isActive = isActive,
        )

    companion object {
        const val STOCK_DELTA_LIMIT = 1_000_000
    }
}
