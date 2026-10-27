package com.aechak.seller.dashboard.request

import com.aechak.application.dashboard.seller.usecase.query.DashboardPeriod
import com.aechak.application.dashboard.seller.usecase.query.SellerDashboardSummaryQuery

/** 기간을 주지 않으면 전체 기간이다 — 로그인 직후 랜딩이 파라미터 없이 들어온다. */
data class SellerDashboardSummaryRequest(
    val period: String? = null,
) {
    fun toQuery(sellerId: Long): SellerDashboardSummaryQuery =
        SellerDashboardSummaryQuery(
            sellerId = sellerId,
            period = period?.let(DashboardPeriod::from) ?: DashboardPeriod.DEFAULT,
        )
}
