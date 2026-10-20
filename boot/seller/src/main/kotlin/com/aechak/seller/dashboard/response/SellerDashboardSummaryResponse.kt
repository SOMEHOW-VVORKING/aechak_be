package com.aechak.seller.dashboard.response

import com.aechak.application.dashboard.seller.usecase.result.SellerDashboardSummaryResult

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
