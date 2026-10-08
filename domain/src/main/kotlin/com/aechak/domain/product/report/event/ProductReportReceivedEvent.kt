package com.aechak.domain.product.report.event

import com.aechak.domain.product.report.enums.ProductReportReason
import com.aechak.domain.support.DomainEvent
import java.time.LocalDateTime

/**
 * 상품 신고가 접수됐다는 사실 이벤트
 * 트리거 : 운영팀 통지 메일 발송
 */
data class ProductReportReceivedEvent(
    val reportId: Long,
    val productPublicId: String,
    val reasonCode: ProductReportReason,
    val reasonText: String?,
    val createdAt: LocalDateTime,
) : DomainEvent
