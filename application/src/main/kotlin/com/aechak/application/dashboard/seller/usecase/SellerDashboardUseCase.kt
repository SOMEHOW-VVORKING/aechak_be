package com.aechak.application.dashboard.seller.usecase

import com.aechak.application.dashboard.seller.usecase.query.SellerDashboardSummaryQuery
import com.aechak.application.dashboard.seller.usecase.result.SellerDashboardSummaryResult

/** 셀러센터 첫 화면 집계 진입점. */
interface SellerDashboardUseCase {
    fun getSummary(query: SellerDashboardSummaryQuery): SellerDashboardSummaryResult
}
