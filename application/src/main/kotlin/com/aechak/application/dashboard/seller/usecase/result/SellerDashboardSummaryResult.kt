package com.aechak.application.dashboard.seller.usecase.result

import com.aechak.application.order.claim.usecase.result.SellerClaimMetricsResult
import com.aechak.application.order.seller.usecase.result.SellerOrderMetricsResult
import com.aechak.application.product.product.usecase.result.SellerProductStatusCountsResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementSummaryResult

/**
 * 셀러센터 첫 화면 집계. 지표는 기간 필터를 받고, 정산·상품 상태는 기간과 무관한 현재 스냅샷이다.
 * 증감률은 서버가 만들지 않는다 — value와 previousValue를 그대로 내리고 화면이 계산한다.
 *
 * 클레임 처리 대기는 요청 대기 세 지표(유형별)와 승인 이후 세 지표(단계별)로 갈려 있다 —
 * 셀러가 숫자만 보고 무엇을 해야 하는지 알 수 있어야 해서 할 일이 다른 단계를 한 칸에 합치지 않는다.
 */
data class SellerDashboardSummaryResult(
    val metrics: Metrics,
    val settlement: SellerSettlementSummaryResult,
    val productStatuses: SellerProductStatusCountsResult,
) {
    data class Metrics(
        val newOrders: Metric,
        val awaitingShipment: Metric,
        val shippingDelayed: Metric,
        val cancelRequests: Metric,
        val returnRequests: Metric,
        val exchangeRequests: Metric,
        val collectionInvoicePending: Metric,
        val collectionCompletionPending: Metric,
        val reshipPending: Metric,
        val unansweredInquiries: Metric,
    )

    /**
     * [previousValue]는 직전 기간 값 — 전체 기간이면 비교 대상이 없어 null이다.
     * [supported]가 false면 값이 아니라 "아직 계산할 수 없다"는 뜻이고 화면이 카드를 흐리게 표시한다.
     */
    data class Metric(
        val value: Long,
        val previousValue: Long?,
        val supported: Boolean = true,
    ) {
        companion object {
            val UNSUPPORTED = Metric(value = 0L, previousValue = null, supported = false)
        }
    }

    companion object {
        fun of(
            orders: SellerOrderMetricsResult,
            previousOrders: SellerOrderMetricsResult?,
            claims: SellerClaimMetricsResult,
            previousClaims: SellerClaimMetricsResult?,
            settlement: SellerSettlementSummaryResult,
            productStatuses: SellerProductStatusCountsResult,
        ): SellerDashboardSummaryResult =
            SellerDashboardSummaryResult(
                metrics =
                    Metrics(
                        newOrders = Metric(orders.newOrders, previousOrders?.newOrders),
                        awaitingShipment = Metric(orders.awaitingShipment, previousOrders?.awaitingShipment),
                        shippingDelayed = Metric(orders.shippingDelayed, previousOrders?.shippingDelayed),
                        cancelRequests = Metric(claims.cancelRequests, previousClaims?.cancelRequests),
                        returnRequests = Metric(claims.returnRequests, previousClaims?.returnRequests),
                        exchangeRequests = Metric(claims.exchangeRequests, previousClaims?.exchangeRequests),
                        collectionInvoicePending =
                            Metric(claims.collectionInvoicePending, previousClaims?.collectionInvoicePending),
                        collectionCompletionPending =
                            Metric(claims.collectionCompletionPending, previousClaims?.collectionCompletionPending),
                        reshipPending = Metric(claims.reshipPending, previousClaims?.reshipPending),
                        unansweredInquiries = Metric.UNSUPPORTED,
                    ),
                settlement = settlement,
                productStatuses = productStatuses,
            )
    }
}
