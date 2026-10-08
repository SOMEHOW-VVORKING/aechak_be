package com.aechak.domain.product.report

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.report.enums.ProductReportReason
import com.aechak.domain.product.report.enums.ProductReportStatus
import com.aechak.domain.product.report.event.ProductReportReceivedEvent
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
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "product_reports",
    uniqueConstraints = [
        UniqueConstraint(name = ProductReport.UK_PRODUCT_ID_REPORTER_ID, columnNames = ["product_id", "reporter_id"]),
    ],
)
class ProductReport protected constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,
    val reporterId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val reasonCode: ProductReportReason,
    @Column(length = REASON_TEXT_MAX)
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

    /** 접수 사실 이벤트 등록. 저장해 id가 생긴 뒤 호출한다 */
    fun registerReceived() =
        registerEvent(
            ProductReportReceivedEvent(
                reportId = id,
                productPublicId = product.publicId,
                reasonCode = reasonCode,
                reasonText = reasonText,
                createdAt = createdAt,
            ),
        )

    companion object {
        const val UK_PRODUCT_ID_REPORTER_ID = "uk_product_reports_product_id_reporter_id"
        const val REASON_TEXT_MAX = 500

        fun report(
            product: Product,
            reporterId: Long,
            reasonCode: ProductReportReason,
            reasonText: String?,
        ): ProductReport {
            if (product.sellerId == reporterId) {
                throw BusinessException(ProductErrorCode.PRODUCT_SELF_REPORT_NOT_ALLOWED)
            }
            val text = reasonText?.trim()?.takeIf { it.isNotEmpty() }
            if (reasonCode == ProductReportReason.OTHER && text == null) {
                throw BusinessException(ProductErrorCode.PRODUCT_REPORT_REASON_TEXT_REQUIRED)
            }
            return ProductReport(product, reporterId, reasonCode, text)
        }
    }
}
