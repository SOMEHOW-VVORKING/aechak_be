package com.aechak.application.dashboard.seller.facade

import com.aechak.application.dashboard.seller.usecase.SellerDashboardUseCase
import com.aechak.application.dashboard.seller.usecase.query.DashboardWindow
import com.aechak.application.dashboard.seller.usecase.query.SellerDashboardSummaryQuery
import com.aechak.application.dashboard.seller.usecase.result.SellerDashboardSummaryResult
import com.aechak.application.order.claim.usecase.SellerClaimUseCase
import com.aechak.application.order.claim.usecase.query.SellerClaimMetricsQuery
import com.aechak.application.order.seller.usecase.SellerOrderUseCase
import com.aechak.application.order.seller.usecase.query.SellerOrderMetricsQuery
import com.aechak.application.product.product.usecase.SellerProductUseCase
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.application.settlement.seller.usecase.SellerSettlementUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.error.SellerErrorCode
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 셀러센터 첫 화면 집계의 유일한 구현체. 네 BC의 진입점을 모아 한 응답으로 조립한다 —
 * 어느 BC도 다른 BC의 테이블을 읽지 않고, 지표 묶음마다 집계 쿼리가 하나씩 돈다.
 *
 * 자격 게이트를 여기서 한 번만 본다. 집계 계약 네 개는 전부 넘겨받은 sellerId 범위만 읽고
 * 식별자를 돌려주지 않으므로 소유권 판정 대상이 아니고, 같은 판정을 BC마다 되풀이하면
 * 화면 한 번에 셀러 상태 조회가 네 번 더 붙는다.
 */
@Service
class SellerDashboardFacade(
    private val sellerUseCase: SellerUseCase,
    private val sellerOrderUseCase: SellerOrderUseCase,
    private val sellerClaimUseCase: SellerClaimUseCase,
    private val sellerSettlementUseCase: SellerSettlementUseCase,
    private val sellerProductUseCase: SellerProductUseCase,
) : SellerDashboardUseCase {
    @Transactional(readOnly = true)
    override fun getSummary(query: SellerDashboardSummaryQuery): SellerDashboardSummaryResult {
        requireDashboardReadableSeller(query.sellerId)
        val window = query.period.windowAt(LocalDateTime.now())
        return SellerDashboardSummaryResult.of(
            orders = orderMetrics(query.sellerId, window.current),
            previousOrders = window.previous?.let { orderMetrics(query.sellerId, it) },
            claims = claimMetrics(query.sellerId, window.current),
            previousClaims = window.previous?.let { claimMetrics(query.sellerId, it) },
            settlement = sellerSettlementUseCase.getSummary(query.sellerId),
            productStatuses = sellerProductUseCase.getMyProductStatusCounts(query.sellerId),
        )
    }

    private fun orderMetrics(
        sellerId: Long,
        range: DashboardWindow.Range?,
    ) = sellerOrderUseCase.getOrderMetrics(
        SellerOrderMetricsQuery(
            sellerId = sellerId,
            orderedFrom = range?.from,
            orderedToExclusive = range?.toExclusive,
        ),
    )

    private fun claimMetrics(
        sellerId: Long,
        range: DashboardWindow.Range?,
    ) = sellerClaimUseCase.getClaimMetrics(
        SellerClaimMetricsQuery(
            sellerId = sellerId,
            requestedFrom = range?.from,
            requestedToExclusive = range?.toExclusive,
        ),
    )

    /** 조회 자격 게이트 — 자격 소멸(탈퇴)과 제재(정지)만 막는다. 휴점·탈퇴신청 셀러는 자기 대시보드를 본다 */
    private fun requireDashboardReadableSeller(sellerId: Long) {
        if (sellerUseCase.getSellerStatus(sellerId) !in DASHBOARD_READABLE_SELLER_STATUSES) {
            throw BusinessException(SellerErrorCode.SELLER_CENTER_READ_FORBIDDEN)
        }
    }

    private companion object {
        private val DASHBOARD_READABLE_SELLER_STATUSES =
            setOf(SellerStatus.ACTIVE, SellerStatus.PAUSED, SellerStatus.WITHDRAWAL_REQUESTED)
    }
}
