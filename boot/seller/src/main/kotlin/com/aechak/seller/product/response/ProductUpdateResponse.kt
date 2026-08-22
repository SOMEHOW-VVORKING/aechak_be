package com.aechak.seller.product.response

import com.aechak.application.product.product.usecase.result.ProductUpdateResult
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

data class ProductUpdateResponse(
    /** products.public_id (ULID) */
    val productId: String,
    val saleStatus: SaleStatus,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(result: ProductUpdateResult): ProductUpdateResponse =
            ProductUpdateResponse(
                productId = result.publicId,
                saleStatus = result.saleStatus,
                updatedAt = result.updatedAt,
            )
    }
}
