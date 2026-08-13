package com.aechak.application.product.usecase.result

import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

data class ProductSaleStatusChangeResult(
    val publicId: String,
    val saleStatus: SaleStatus,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun of(product: Product): ProductSaleStatusChangeResult =
            ProductSaleStatusChangeResult(
                publicId = product.publicId,
                saleStatus = product.saleStatus,
                updatedAt = product.updatedAt,
            )
    }
}
