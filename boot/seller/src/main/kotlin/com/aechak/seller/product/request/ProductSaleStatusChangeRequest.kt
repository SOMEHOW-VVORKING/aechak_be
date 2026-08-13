package com.aechak.seller.product.request

import com.aechak.application.product.usecase.command.ChangeProductSaleStatusCommand
import com.aechak.domain.product.product.enums.SaleStatus

data class ProductSaleStatusChangeRequest(
    val saleStatus: SaleStatus,
) {
    fun toCommand(
        sellerId: Long,
        productPublicId: String,
    ): ChangeProductSaleStatusCommand =
        ChangeProductSaleStatusCommand(
            sellerId = sellerId,
            productPublicId = productPublicId,
            saleStatus = saleStatus,
        )
}
