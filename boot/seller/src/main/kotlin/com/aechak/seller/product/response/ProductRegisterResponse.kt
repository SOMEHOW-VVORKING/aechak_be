package com.aechak.seller.product.response

import com.aechak.application.product.usecase.result.ProductRegisterResult
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

data class ProductRegisterResponse(
    /** products.public_id (ULID) */
    val productId: String,
    val saleStatus: SaleStatus,
    val inspectionStatus: InspectionStatus,
    val versionNo: Int,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(result: ProductRegisterResult): ProductRegisterResponse =
            ProductRegisterResponse(
                productId = result.publicId,
                saleStatus = result.saleStatus,
                inspectionStatus = result.inspectionStatus,
                versionNo = result.versionNo,
                createdAt = result.createdAt,
            )
    }
}
