package com.aechak.application.product.report.usecase.command

import com.aechak.domain.product.product.Product
import com.aechak.domain.product.report.ProductReport
import com.aechak.domain.product.report.enums.ProductReportReason

data class SubmitProductReportCommand(
    val reporterId: Long,
    val productPublicId: String,
    val reasonCode: ProductReportReason,
    val reasonText: String?,
) {
    fun toEntity(product: Product): ProductReport = ProductReport.report(product, reporterId, reasonCode, reasonText)
}
