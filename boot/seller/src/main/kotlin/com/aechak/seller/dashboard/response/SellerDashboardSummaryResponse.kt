package com.aechak.seller.dashboard.response

import com.aechak.application.dashboard.seller.usecase.result.SellerDashboardSummaryResult

/**
 * 셀러센터 첫 화면 집계 응답. 클레임 처리 대기는 요청 대기 세 지표(유형별)와 승인 이후 세 지표(단계별)로 갈린다 —
 * 셀러가 숫자만 보고 다음에 할 일을 알 수 있어야 해서 할 일이 다른 단계를 한 칸에 합치지 않는다.
 */
data class SellerDashboardSummaryResponse(
    val metrics: MetricsResponse,
    val settlement: SettlementSummaryResponse,
    val productStatuses: ProductStatusesResponse,
) {
    data class MetricsResponse(
        val newOrders: MetricResponse,
        val awaitingShipment: MetricResponse,
        val shippingDelayed: MetricResponse,
        val cancelRequests: MetricResponse,
        val returnRequests: MetricResponse,
        val exchangeRequests: MetricResponse,
        val collectionInvoicePending: MetricResponse,
        val collectionCompletionPending: MetricResponse,
        val reshipPending: MetricResponse,
        val unansweredInquiries: MetricResponse,
    )

    /** previousValue가 null이면 비교할 직전 기간이 없다(period=all). 증감률은 화면이 계산한다. */
    data class MetricResponse(
        val value: Long,
        val previousValue: Long?,
        val supported: Boolean,
    ) {
        companion object {
            fun from(metric: SellerDashboardSummaryResult.Metric): MetricResponse =
                MetricResponse(value = metric.value, previousValue = metric.previousValue, supported = metric.supported)
        }
    }

    data class SettlementSummaryResponse(
        val totalAmount: Long,
        val pendingAmount: Long,
        val onHoldAmount: Long,
    )

    data class ProductStatusesResponse(
        val awaitingInspection: Long,
        val rejected: Long,
        val suspended: Long,
        val soldOut: Long,
    )

    companion object {
        fun from(result: SellerDashboardSummaryResult): SellerDashboardSummaryResponse =
            SellerDashboardSummaryResponse(
                metrics =
                    MetricsResponse(
                        newOrders = MetricResponse.from(result.metrics.newOrders),
                        awaitingShipment = MetricResponse.from(result.metrics.awaitingShipment),
                        shippingDelayed = MetricResponse.from(result.metrics.shippingDelayed),
                        cancelRequests = MetricResponse.from(result.metrics.cancelRequests),
                        returnRequests = MetricResponse.from(result.metrics.returnRequests),
                        exchangeRequests = MetricResponse.from(result.metrics.exchangeRequests),
                        collectionInvoicePending = MetricResponse.from(result.metrics.collectionInvoicePending),
                        collectionCompletionPending = MetricResponse.from(result.metrics.collectionCompletionPending),
                        reshipPending = MetricResponse.from(result.metrics.reshipPending),
                        unansweredInquiries = MetricResponse.from(result.metrics.unansweredInquiries),
                    ),
                settlement =
                    SettlementSummaryResponse(
                        totalAmount = result.settlement.totalAmount,
                        pendingAmount = result.settlement.pendingAmount,
                        onHoldAmount = result.settlement.onHoldAmount,
                    ),
                productStatuses =
                    ProductStatusesResponse(
                        awaitingInspection = result.productStatuses.awaitingInspection,
                        rejected = result.productStatuses.rejected,
                        suspended = result.productStatuses.suspended,
                        soldOut = result.productStatuses.soldOut,
                    ),
            )
    }
}
