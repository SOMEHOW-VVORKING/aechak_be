package com.aechak.application.order.admin.port

import com.aechak.application.order.admin.port.view.AdminOrderMetricsCountView

interface AdminOrderMetricsQueryPort {
    fun countMetrics(condition: AdminOrderMetricsCondition): AdminOrderMetricsCountView

    /** 주문 시점 단가 × 수량의 합. 품목 조인이 필요해 건수 집계와 쿼리를 나눈다 */
    fun sumProductAmount(condition: AdminOrderMetricsCondition): Long
}
