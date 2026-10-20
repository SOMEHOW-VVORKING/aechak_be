package com.aechak.application.order.seller.port

import com.aechak.application.order.seller.port.view.SellerOrderMetricsView

/** 셀러 대시보드의 주문 지표 집계 포트. 세 지표를 쿼리 한 번으로 받는다. */
interface SellerOrderMetricsQueryPort {
    fun countMetrics(condition: SellerOrderMetricsCondition): SellerOrderMetricsView
}
