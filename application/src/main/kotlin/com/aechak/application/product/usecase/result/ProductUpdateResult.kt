package com.aechak.application.product.usecase.result

import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

data class ProductUpdateResult(
    val publicId: String,
    val saleStatus: SaleStatus,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun of(product: Product): ProductUpdateResult =
            ProductUpdateResult(
                publicId = product.publicId,
                saleStatus = product.saleStatus,
                updatedAt = product.updatedAt,
            )
    }
}
