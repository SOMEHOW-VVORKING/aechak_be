package com.aechak.application.dashboard.seller

import com.aechak.application.dashboard.seller.facade.SellerDashboardFacade
import com.aechak.application.dashboard.seller.usecase.query.DashboardPeriod
import com.aechak.application.dashboard.seller.usecase.query.SellerDashboardSummaryQuery
import com.aechak.application.order.claim.usecase.SellerClaimUseCase
import com.aechak.application.order.claim.usecase.command.ApproveClaimCommand
import com.aechak.application.order.claim.usecase.command.CompleteClaimCollectionCommand
import com.aechak.application.order.claim.usecase.command.RejectClaimCommand
import com.aechak.application.order.claim.usecase.command.ReshipClaimCommand
import com.aechak.application.order.claim.usecase.command.StartClaimCollectionCommand
import com.aechak.application.order.claim.usecase.command.UpdateClaimMemoCommand
import com.aechak.application.order.claim.usecase.query.SellerClaimMetricsQuery
import com.aechak.application.order.claim.usecase.query.SellerClaimSearchQuery
import com.aechak.application.order.claim.usecase.result.ReshipClaimResult
import com.aechak.application.order.claim.usecase.result.SellerClaimDetailResult
import com.aechak.application.order.claim.usecase.result.SellerClaimMetricsResult
import com.aechak.application.order.claim.usecase.result.SellerClaimPageResult
import com.aechak.application.order.seller.usecase.SellerOrderUseCase
import com.aechak.application.order.seller.usecase.command.AcceptOrderCommand
import com.aechak.application.order.seller.usecase.command.CompleteDeliveryCommand
import com.aechak.application.order.seller.usecase.command.RegisterShipmentCommand
import com.aechak.application.order.seller.usecase.query.SellerOrderMetricsQuery
import com.aechak.application.order.seller.usecase.query.SellerOrderSearchQuery
import com.aechak.application.order.seller.usecase.result.RegisterShipmentResult
import com.aechak.application.order.seller.usecase.result.SellerOrderDetailResult
import com.aechak.application.order.seller.usecase.result.SellerOrderMetricsResult
import com.aechak.application.order.seller.usecase.result.SellerOrderSummaryResult
import com.aechak.application.product.product.usecase.SellerProductUseCase
import com.aechak.application.product.product.usecase.command.AppealProductInspectionCommand
import com.aechak.application.product.product.usecase.command.ChangeOptionCombinationCommand
import com.aechak.application.product.product.usecase.command.ChangeProductSaleStatusCommand
import com.aechak.application.product.product.usecase.command.RegisterProductCommand
import com.aechak.application.product.product.usecase.command.UpdateProductCommand
import com.aechak.application.product.product.usecase.query.SellerProductSearchQuery
import com.aechak.application.product.product.usecase.result.OptionCombinationChangeResult
import com.aechak.application.product.product.usecase.result.ProductRegisterResult
import com.aechak.application.product.product.usecase.result.ProductSaleStatusChangeResult
import com.aechak.application.product.product.usecase.result.ProductUpdateResult
import com.aechak.application.product.product.usecase.result.SellerProductDetailResult
import com.aechak.application.product.product.usecase.result.SellerProductInspectionResult
import com.aechak.application.product.product.usecase.result.SellerProductOptionsResult
import com.aechak.application.product.product.usecase.result.SellerProductStatusCountsResult
import com.aechak.application.product.product.usecase.result.SellerProductSummaryResult
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.application.settlement.seller.usecase.SellerSettlementUseCase
import com.aechak.application.settlement.seller.usecase.query.SellerSettlementSearchQuery
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementDetailResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementListItemResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementMonthsResult
import com.aechak.application.settlement.seller.usecase.result.SellerSettlementSummaryResult
import com.aechak.application.support.OffsetPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.error.SellerErrorCode
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 네 BC를 모으는 조립 규칙만 검증한다 — 집계 값 자체는 각 BC의 어댑터 테스트와 통합 테스트가 본다.
 */
class SellerDashboardFacadeTest {
    private val orders = RecordingOrderUseCase()
    private val claims = RecordingClaimUseCase()
    private val settlement = RecordingSettlementUseCase()
    private val products = RecordingProductUseCase()

    @Test
    fun `한 응답의 현재 기간과 직전 기간은 같은 기준 시각을 쓴다`() {
        facade(SellerStatus.ACTIVE).getSummary(SellerDashboardSummaryQuery(SELLER_ID, DashboardPeriod.LAST_7_DAYS))

        assertEquals(2, orders.queries.size, "직전 기간이 있는 기간은 주문 집계를 두 번 돈다")
        assertEquals(
            orders.queries[0].at,
            orders.queries[1].at,
            "지표마다 시계를 다시 뜨면 발송 기한 경계의 주문이 현재·직전 기간에서 다르게 판정된다",
        )
    }

    @Test
    fun `직전 기간은 현재 기간과 같은 길이로 맞붙는다`() {
        facade(SellerStatus.ACTIVE).getSummary(SellerDashboardSummaryQuery(SELLER_ID, DashboardPeriod.LAST_30_DAYS))

        val current = orders.queries[0]
        val previous = orders.queries[1]
        assertEquals(current.at, current.orderedToExclusive)
        assertEquals(current.orderedFrom, previous.orderedToExclusive)
        assertEquals(current.orderedFrom!!.minusDays(30), previous.orderedFrom)
    }

    @Test
    fun `전체 기간은 날짜 축 없이 한 번만 집계한다`() {
        facade(SellerStatus.ACTIVE).getSummary(SellerDashboardSummaryQuery(SELLER_ID, DashboardPeriod.ALL))

        assertEquals(1, orders.queries.size, "비교할 직전 기간이 없는데 재질의하면 쿼리가 두 배가 된다")
        assertEquals(1, claims.queries.size)
        assertEquals(null, orders.queries[0].orderedFrom)
        assertEquals(null, claims.queries[0].requestedFrom)
    }

    @Test
    fun `클레임 집계도 주문 집계와 같은 구간을 받는다`() {
        facade(SellerStatus.ACTIVE).getSummary(SellerDashboardSummaryQuery(SELLER_ID, DashboardPeriod.LAST_90_DAYS))

        assertEquals(orders.queries[0].orderedFrom, claims.queries[0].requestedFrom)
        assertEquals(orders.queries[1].orderedFrom, claims.queries[1].requestedFrom)
    }

    @Test
    fun `모든 집계는 요청자의 셀러 범위로만 묻는다`() {
        facade(SellerStatus.ACTIVE).getSummary(SellerDashboardSummaryQuery(SELLER_ID, DashboardPeriod.LAST_7_DAYS))

        assertTrue(orders.queries.all { it.sellerId == SELLER_ID })
        assertTrue(claims.queries.all { it.sellerId == SELLER_ID })
        assertEquals(listOf(SELLER_ID), settlement.sellerIds)
        assertEquals(listOf(SELLER_ID), products.sellerIds)
    }

    @Test
    fun `휴점과 탈퇴 신청 셀러도 자기 대시보드를 본다`() {
        listOf(SellerStatus.ACTIVE, SellerStatus.PAUSED, SellerStatus.WITHDRAWAL_REQUESTED).forEach { status ->
            facade(status).getSummary(SellerDashboardSummaryQuery(SELLER_ID, DashboardPeriod.ALL))
        }
    }

    @Test
    fun `제재와 자격 소멸 상태는 집계를 한 번도 묻지 않고 거절한다`() {
        listOf(SellerStatus.SUSPENDED, SellerStatus.WITHDRAWN).forEach { status ->
            val exception =
                assertThrows<BusinessException> {
                    facade(status).getSummary(SellerDashboardSummaryQuery(SELLER_ID, DashboardPeriod.ALL))
                }
            assertEquals(SellerErrorCode.SELLER_CENTER_READ_FORBIDDEN, exception.errorCode)
        }
        assertTrue(orders.queries.isEmpty(), "게이트가 집계보다 뒤에 있으면 거절된 요청도 쿼리를 태운다")
        assertTrue(claims.queries.isEmpty(), "집계 하나만 게이트 앞으로 올라가도 거절된 요청이 그 BC를 읽는다")
        assertTrue(settlement.sellerIds.isEmpty())
        assertTrue(products.sellerIds.isEmpty())
    }

    /**
     * 셀러가 아예 아닌 경우는 제재와 다른 코드로 나간다 — 같은 10003으로 뭉개면 입점한 적 없는
     * 계정에 "셀러센터 조회 권한이 없다"는 안내가 나가고 프런트가 입점 신청으로 보낼 수 없다.
     */
    @Test
    fun `셀러가 아닌 계정은 제재와 다른 코드로 거절한다`() {
        val exception =
            assertThrows<BusinessException> {
                facade(null).getSummary(SellerDashboardSummaryQuery(SELLER_ID, DashboardPeriod.ALL))
            }

        assertEquals(SellerErrorCode.NOT_SELLER, exception.errorCode)
        assertTrue(orders.queries.isEmpty())
    }

    private fun facade(status: SellerStatus?) =
        SellerDashboardFacade(
            sellerUseCase = FixedSellerUseCase(status),
            sellerOrderUseCase = orders,
            sellerClaimUseCase = claims,
            sellerSettlementUseCase = settlement,
            sellerProductUseCase = products,
        )

    private class FixedSellerUseCase(
        private val status: SellerStatus?,
    ) : SellerUseCase {
        override fun requireSellerStatus(userId: Long): SellerStatus = status ?: throw BusinessException(SellerErrorCode.NOT_SELLER)
    }

    private class RecordingOrderUseCase : SellerOrderUseCase {
        val queries = mutableListOf<SellerOrderMetricsQuery>()

        override fun getOrderMetrics(query: SellerOrderMetricsQuery): SellerOrderMetricsResult {
            queries += query
            return SellerOrderMetricsResult(newOrders = 0L, awaitingShipment = 0L, shippingDelayed = 0L)
        }

        override fun acceptOrder(command: AcceptOrderCommand) = unused()

        override fun getMyOrders(query: SellerOrderSearchQuery): OffsetPageResult<SellerOrderSummaryResult> = unused()

        override fun getMyOrderDetail(
            sellerId: Long,
            orderPublicId: String,
        ): SellerOrderDetailResult = unused()

        override fun registerShipment(command: RegisterShipmentCommand): RegisterShipmentResult = unused()

        override fun completeDelivery(command: CompleteDeliveryCommand) = unused()
    }

    private class RecordingClaimUseCase : SellerClaimUseCase {
        val queries = mutableListOf<SellerClaimMetricsQuery>()

        override fun getClaimMetrics(query: SellerClaimMetricsQuery): SellerClaimMetricsResult {
            queries += query
            return SellerClaimMetricsResult(
                cancelRequests = 0L,
                returnRequests = 0L,
                exchangeRequests = 0L,
                collectionInvoicePending = 0L,
                collectionCompletionPending = 0L,
                reshipPending = 0L,
            )
        }

        override fun approveClaim(command: ApproveClaimCommand) = unused()

        override fun rejectClaim(command: RejectClaimCommand) = unused()

        override fun searchClaims(query: SellerClaimSearchQuery): SellerClaimPageResult = unused()

        override fun getClaim(
            claimPublicId: String,
            sellerId: Long,
        ): SellerClaimDetailResult = unused()

        override fun updateSellerMemo(command: UpdateClaimMemoCommand) = unused()

        override fun startCollection(command: StartClaimCollectionCommand) = unused()

        override fun completeCollection(command: CompleteClaimCollectionCommand) = unused()

        override fun reship(command: ReshipClaimCommand): ReshipClaimResult = unused()
    }

    private class RecordingSettlementUseCase : SellerSettlementUseCase {
        val sellerIds = mutableListOf<Long>()

        override fun getSummary(sellerId: Long): SellerSettlementSummaryResult {
            sellerIds += sellerId
            return SellerSettlementSummaryResult(totalAmount = 0L, pendingAmount = 0L, onHoldAmount = 0L)
        }

        override fun searchSettlements(query: SellerSettlementSearchQuery): OffsetPageResult<SellerSettlementListItemResult> = unused()

        override fun getSettlement(
            orderPublicId: String,
            sellerId: Long,
        ): SellerSettlementDetailResult = unused()

        override fun getSettlementMonths(sellerId: Long): SellerSettlementMonthsResult = unused()
    }

    private class RecordingProductUseCase : SellerProductUseCase {
        val sellerIds = mutableListOf<Long>()

        override fun getMyProductStatusCounts(sellerId: Long): SellerProductStatusCountsResult {
            sellerIds += sellerId
            return SellerProductStatusCountsResult(awaitingInspection = 0L, rejected = 0L, suspended = 0L, soldOut = 0L)
        }

        override fun registerProduct(command: RegisterProductCommand): ProductRegisterResult = unused()

        override fun updateProduct(command: UpdateProductCommand): ProductUpdateResult = unused()

        override fun changeProductSaleStatus(command: ChangeProductSaleStatusCommand): ProductSaleStatusChangeResult = unused()

        override fun changeOptionCombination(command: ChangeOptionCombinationCommand): OptionCombinationChangeResult = unused()

        override fun getMyProducts(query: SellerProductSearchQuery): OffsetPageResult<SellerProductSummaryResult> = unused()

        override fun getMyProductDetail(
            sellerId: Long,
            publicId: String,
        ): SellerProductDetailResult = unused()

        override fun getMyProductInspection(
            sellerId: Long,
            publicId: String,
        ): SellerProductInspectionResult = unused()

        override fun getMyProductOptions(
            sellerId: Long,
            publicId: String,
        ): SellerProductOptionsResult = unused()

        override fun appealMyProductInspection(command: AppealProductInspectionCommand): SellerProductInspectionResult = unused()
    }

    private companion object {
        const val SELLER_ID = 7L

        fun unused(): Nothing = error("대시보드 조립이 쓰지 않는 계약이다")
    }
}
