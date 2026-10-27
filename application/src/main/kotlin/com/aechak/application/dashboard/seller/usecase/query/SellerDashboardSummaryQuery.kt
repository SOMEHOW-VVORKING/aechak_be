package com.aechak.application.dashboard.seller.usecase.query

data class SellerDashboardSummaryQuery(
    val sellerId: Long,
    val period: DashboardPeriod,
)
