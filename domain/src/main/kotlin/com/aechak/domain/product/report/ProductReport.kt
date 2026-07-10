package com.aechak.domain.product.report

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.Product
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import com.aechak.domain.product.report.enums.ProductReportStatus
import com.aechak.domain.product.report.enums.ProductReportReason

@Entity
@Table(name = "product_reports")
class ProductReport protected constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,
    val reporterId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val reasonCode: ProductReportReason,
    @Column(length = 500)
    val reasonText: String?,
) : AggregateRoot() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ProductReportStatus = ProductReportStatus.RECEIVED
        protected set

    fun forward() {
        if (status != ProductReportStatus.RECEIVED) {
            throw BusinessException(ProductErrorCode.INVALID_PRODUCT_REPORT_STATUS_TRANSITION)
        }
        status = ProductReportStatus.FORWARDED
    }

    companion object {
        fun report(
            product: Product,
            reporterId: Long,
            reasonCode: ProductReportReason,
            reasonText: String?,
        ): ProductReport {
            if (reasonCode == ProductReportReason.OTHER && reasonText.isNullOrBlank()) {
                throw BusinessException(ProductErrorCode.PRODUCT_REPORT_REASON_TEXT_REQUIRED)
            }
            return ProductReport(product, reporterId, reasonCode, reasonText)
        }
    }
}
