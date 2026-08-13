package com.aechak.application.product.usecase.command

import com.aechak.domain.product.product.enums.SaleStatus

data class ChangeProductSaleStatusCommand(
    val sellerId: Long,
    val productPublicId: String,
    val saleStatus: SaleStatus,
)
