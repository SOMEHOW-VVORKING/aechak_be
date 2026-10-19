package com.aechak.application.order.seller.service

import com.aechak.application.order.seller.port.SellerOrderMetricsCondition
import com.aechak.application.order.seller.port.SellerOrderMetricsQueryPort
import com.aechak.application.order.seller.port.view.SellerOrderMetricsView
import com.aechak.application.order.seller.usecase.query.SellerOrderMetricsQuery
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 셀러 대시보드 주문 지표 집계.
 *
 * 발송 기한은 주문일에서 센다 — orders.updated_at은 구매확정 기한 부여 같은 뒤따르는 쓰기에도 갱신돼
 * 지연 건이 조용히 지연에서 빠진다. 셀러가 약속한 발송 기한의 기준도 결제 시각이다.
 */
@Service
class SellerOrderMetricsService(
    private val sellerOrderMetricsQueryPort: SellerOrderMetricsQueryPort,
    @param:Value("\${order.dispatch.sla-days:3}") private val dispatchSlaDays: Long,
) {
    fun countMetrics(
        query: SellerOrderMetricsQuery,
        now: LocalDateTime,
    ): SellerOrderMetricsView =
        sellerOrderMetricsQueryPort.countMetrics(
            SellerOrderMetricsCondition(
                sellerId = query.sellerId,
                orderedFrom = query.orderedFrom,
                orderedToExclusive = query.orderedToExclusive,
                dispatchDeadline = now.minusDays(dispatchSlaDays),
            ),
        )
}
