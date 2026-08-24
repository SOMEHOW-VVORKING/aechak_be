package com.aechak.seller.product.response

import com.aechak.application.product.product.usecase.result.ProductSaleStatusChangeResult
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

data class ProductSaleStatusChangeResponse(
    /** products.public_id (ULID) */
    val productId: String,
    val saleStatus: SaleStatus,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(result: ProductSaleStatusChangeResult): ProductSaleStatusChangeResponse =
            ProductSaleStatusChangeResponse(
                productId = result.publicId,
                saleStatus = result.saleStatus,
                updatedAt = result.updatedAt,
            )
    }
}
