package com.aechak.application.product.usecase.result

import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

data class ProductRegisterResult(
    val publicId: String,
    val saleStatus: SaleStatus,
    val inspectionStatus: InspectionStatus,
    val versionNo: Int,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(
            product: Product,
            versionNo: Int,
        ): ProductRegisterResult =
            ProductRegisterResult(
                publicId = product.publicId,
                saleStatus = product.saleStatus,
                inspectionStatus = product.inspectionStatus,
                versionNo = versionNo,
                createdAt = product.createdAt,
            )
    }
}
