package com.aechak.seller.dashboard

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.pii.support.PiiStringCodec
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimItem
import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.settlement.settlement.Settlement
import com.aechak.domain.settlement.settlement.enums.SettlementStatus
import org.hamcrest.Matchers.nullValue
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 셀러 대시보드 집계 통합. 깨지면 셀러센터 첫 화면이 깨진 것이다 —
 * 지표별 계산, 셀러 범위 격리, 데이터 없는 새 셀러, 기간 필터와 직전 기간, 쿼리 수 상한이 대상이다.
 */
class SellerDashboardSummaryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var piiStringCodec: PiiStringCodec

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private var buyerUserId = 0L
    private lateinit var token: String
    private var settlementSeq = 0L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        sellerUserId = createActiveUser()
        token = mintAccessToken(sellerUserId)
        openSeller(sellerUserId)
        buyerUserId = createActiveUser()
    }

    @Test
    fun `데이터가 없는 새 셀러도 전부 0으로 정상 응답한다`() {
        summary()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.metrics.newOrders.value").value(0))
            .andExpect(jsonPath("$.data.metrics.awaitingShipment.value").value(0))
            .andExpect(jsonPath("$.data.metrics.shippingDelayed.value").value(0))
            .andExpect(jsonPath("$.data.metrics.cancelRequests.value").value(0))
            .andExpect(jsonPath("$.data.metrics.returnRequests.value").value(0))
            .andExpect(jsonPath("$.data.metrics.exchangeRequests.value").value(0))
            .andExpect(jsonPath("$.data.settlement.totalAmount").value(0))
            .andExpect(jsonPath("$.data.settlement.pendingAmount").value(0))
            .andExpect(jsonPath("$.data.settlement.onHoldAmount").value(0))
            .andExpect(jsonPath("$.data.productStatuses.awaitingInspection").value(0))
            .andExpect(jsonPath("$.data.productStatuses.rejected").value(0))
            .andExpect(jsonPath("$.data.productStatuses.suspended").value(0))
            .andExpect(jsonPath("$.data.productStatuses.soldOut").value(0))
    }

    @Test
    fun `신규 주문은 결제완료 건만 센다`() {
        seedOrder(status = OrderStatus.PAID)
        seedOrder(status = OrderStatus.PAID)
        seedOrder(status = OrderStatus.PREPARING)
        seedOrder(status = OrderStatus.PENDING_PAYMENT)

        summary()
            .andExpect(jsonPath("$.data.metrics.newOrders.value").value(2))
    }

    @Test
    fun `발송 대기는 상품 준비중과 발송 대기 상태를 함께 센다`() {
        seedOrder(status = OrderStatus.PREPARING)
        seedOrder(status = OrderStatus.DISPATCH_PENDING)
        seedOrder(status = OrderStatus.SHIPPING)
        seedOrder(status = OrderStatus.PAID)

        summary()
            .andExpect(jsonPath("$.data.metrics.awaitingShipment.value").value(2))
    }

    @Test
    fun `배송 지연은 발송 기한이 지난 미발송 건만 센다`() {
        seedOrder(status = OrderStatus.DISPATCH_PENDING, orderedAt = LocalDateTime.now().minusDays(10))
        seedOrder(status = OrderStatus.PREPARING, orderedAt = LocalDateTime.now().minusDays(10))
        seedOrder(status = OrderStatus.DISPATCH_PENDING, orderedAt = LocalDateTime.now().minusHours(1))
        seedOrder(status = OrderStatus.SHIPPING, orderedAt = LocalDateTime.now().minusDays(10))

        summary()
            .andExpect(jsonPath("$.data.metrics.awaitingShipment.value").value(3))
            .andExpect(jsonPath("$.data.metrics.shippingDelayed.value").value(2))
    }

    @Test
    fun `클레임 지표는 셀러 미응답 건을 유형별로 센다`() {
        val cancelOne = seedOrder(status = OrderStatus.PAID)
        val cancelTwo = seedOrder(status = OrderStatus.PAID)
        val returned = seedOrder(status = OrderStatus.PAID)
        val exchanged = seedOrder(status = OrderStatus.PAID)
        val approved = seedOrder(status = OrderStatus.PAID)
        seedClaim(cancelOne, ClaimType.CANCEL)
        seedClaim(cancelTwo, ClaimType.CANCEL)
        seedClaim(returned, ClaimType.RETURN)
        seedClaim(exchanged, ClaimType.EXCHANGE)
        seedClaim(approved, ClaimType.CANCEL, status = ClaimStatus.APPROVED)

        summary()
            .andExpect(jsonPath("$.data.metrics.cancelRequests.value").value(2))
            .andExpect(jsonPath("$.data.metrics.returnRequests.value").value(1))
            .andExpect(jsonPath("$.data.metrics.exchangeRequests.value").value(1))
    }

    @Test
    fun `정산 요약은 대기와 보류만 담고 예정 금액은 둘의 합이다`() {
        seedSettlement(netAmount = 300_000L, status = SettlementStatus.PENDING)
        seedSettlement(netAmount = 120_000L, status = SettlementStatus.PENDING)
        seedSettlement(netAmount = 48_000L, status = SettlementStatus.ON_HOLD)
        seedSettlement(netAmount = 900_000L, status = SettlementStatus.PAID)
        seedSettlement(netAmount = 70_000L, status = SettlementStatus.CANCELLED)

        summary()
            .andExpect(jsonPath("$.data.settlement.pendingAmount").value(420_000))
            .andExpect(jsonPath("$.data.settlement.onHoldAmount").value(48_000))
            .andExpect(jsonPath("$.data.settlement.totalAmount").value(468_000))
    }

    @Test
    fun `상품 상태 요약은 검수 대기 반려 판매중지 품절을 각각 센다`() {
        seedProduct(inspectionStatus = InspectionStatus.PENDING, stock = 10)
        seedProduct(inspectionStatus = InspectionStatus.PENDING, stock = 10)
        seedProduct(inspectionStatus = InspectionStatus.REJECTED, stock = 10)
        seedProduct(saleStatus = SaleStatus.SUSPENDED, stock = 10)
        seedProduct(stock = 0)
        seedProduct(stock = 0)
        seedProduct(stock = 0)
        seedProduct(stock = 5)

        summary()
            .andExpect(jsonPath("$.data.productStatuses.awaitingInspection").value(2))
            .andExpect(jsonPath("$.data.productStatuses.rejected").value(1))
            .andExpect(jsonPath("$.data.productStatuses.suspended").value(1))
            .andExpect(jsonPath("$.data.productStatuses.soldOut").value(3))
    }

    @Test
    fun `품절은 비활성 조합의 재고를 재고로 보지 않는다`() {
        val onlyInactiveStock = seedProduct(stock = 10)
        deactivateCombinations(onlyInactiveStock)
        seedProduct(stock = 10)

        summary()
            .andExpect(jsonPath("$.data.productStatuses.soldOut").value(1))
    }

    @Test
    fun `타 셀러 데이터는 어느 지표에도 섞이지 않는다`() {
        val otherSeller = createActiveUser().also { openSeller(it) }
        seedOrder(status = OrderStatus.PAID, sellerId = otherSeller)
        seedClaim(seedOrder(status = OrderStatus.PAID, sellerId = otherSeller), ClaimType.CANCEL)
        seedSettlement(netAmount = 500_000L, status = SettlementStatus.PENDING, sellerId = otherSeller)
        seedProduct(inspectionStatus = InspectionStatus.PENDING, stock = 10, sellerId = otherSeller)

        summary()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.metrics.newOrders.value").value(0))
            .andExpect(jsonPath("$.data.metrics.cancelRequests.value").value(0))
            .andExpect(jsonPath("$.data.settlement.pendingAmount").value(0))
            .andExpect(jsonPath("$.data.productStatuses.awaitingInspection").value(0))
    }

    @Test
    fun `미응답 문의는 미지원으로 내려간다`() {
        summary()
            .andExpect(jsonPath("$.data.metrics.unansweredInquiries.supported").value(false))
            .andExpect(jsonPath("$.data.metrics.unansweredInquiries.value").value(0))
            .andExpect(jsonPath("$.data.metrics.unansweredInquiries.previousValue").value(nullValue()))
            .andExpect(jsonPath("$.data.metrics.newOrders.supported").value(true))
    }

    @Test
    fun `전체 기간에는 직전 기간 값이 없다`() {
        seedOrder(status = OrderStatus.PAID, orderedAt = LocalDateTime.now().minusDays(200))

        summary()
            .andExpect(jsonPath("$.data.metrics.newOrders.value").value(1))
            .andExpect(jsonPath("$.data.metrics.newOrders.previousValue").value(nullValue()))
            .andExpect(jsonPath("$.data.metrics.cancelRequests.previousValue").value(nullValue()))
    }

    @Test
    fun `기간 필터는 주문일로 자르고 직전 기간을 함께 내린다`() {
        seedOrder(status = OrderStatus.PAID, orderedAt = LocalDateTime.now().minusDays(2))
        seedOrder(status = OrderStatus.PAID, orderedAt = LocalDateTime.now().minusDays(3))
        seedOrder(status = OrderStatus.PAID, orderedAt = LocalDateTime.now().minusDays(10))
        seedOrder(status = OrderStatus.PAID, orderedAt = LocalDateTime.now().minusDays(20))

        summary("7d")
            .andExpect(jsonPath("$.data.metrics.newOrders.value").value(2))
            .andExpect(jsonPath("$.data.metrics.newOrders.previousValue").value(1))

        summary("30d")
            .andExpect(jsonPath("$.data.metrics.newOrders.value").value(4))
            .andExpect(jsonPath("$.data.metrics.newOrders.previousValue").value(0))

        summary("90d")
            .andExpect(jsonPath("$.data.metrics.newOrders.value").value(4))

        summary("all")
            .andExpect(jsonPath("$.data.metrics.newOrders.value").value(4))
    }

    @Test
    fun `클레임 지표도 요청일 기준으로 기간을 자른다`() {
        val recent = seedOrder(status = OrderStatus.PAID)
        val older = seedOrder(status = OrderStatus.PAID)
        seedClaim(recent, ClaimType.CANCEL, requestedAt = LocalDateTime.now().minusDays(2))
        seedClaim(older, ClaimType.CANCEL, requestedAt = LocalDateTime.now().minusDays(10))

        summary("7d")
            .andExpect(jsonPath("$.data.metrics.cancelRequests.value").value(1))
            .andExpect(jsonPath("$.data.metrics.cancelRequests.previousValue").value(1))
    }

    @Test
    fun `정산과 상품 상태는 기간 필터와 무관한 현재 스냅샷이다`() {
        seedSettlement(netAmount = 300_000L, status = SettlementStatus.PENDING)
        seedProduct(inspectionStatus = InspectionStatus.PENDING, stock = 10)

        summary("7d")
            .andExpect(jsonPath("$.data.settlement.pendingAmount").value(300_000))
            .andExpect(jsonPath("$.data.productStatuses.awaitingInspection").value(1))
    }

    @Test
    fun `지원하지 않는 기간 코드는 400으로 거절한다`() {
        summary("1d")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `휴점 셀러도 자기 대시보드를 본다`() {
        changeSellerStatus(sellerUserId, SellerStatus.PAUSED)

        summary().andExpect(status().isOk)
    }

    @Test
    fun `정지 셀러는 대시보드를 조회할 수 없다`() {
        changeSellerStatus(sellerUserId, SellerStatus.SUSPENDED)

        summary()
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(10003))
    }

    @Test
    fun `셀러가 아닌 계정은 대시보드를 조회할 수 없다`() {
        val notSeller = mintAccessToken(createActiveUser())

        summary(asToken = notSeller)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(10003))
    }

    /**
     * 집계 쿼리 수가 데이터 양과 무관함을 고정한다 — 지표마다 단건 집계를 돌리면 깨진다.
     * 전제: 묶음(주문·클레임·정산·상품)마다 조건부 합계 쿼리 하나 + 직전 기간 재질의뿐이다.
     * 통계는 프로퍼티가 아니라 런타임에 켠다 — properties를 바꾸면 컨텍스트가 갈라져 공유 MySQL 컨테이너가 조기 종료될 수 있다.
     */
    @Nested
    inner class SummaryQueryCount {
        private lateinit var statistics: Statistics

        @BeforeEach
        fun enableStatistics() {
            statistics = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
            statistics.isStatisticsEnabled = true
        }

        @Test
        fun `데이터가 늘어도 집계 쿼리 수는 그대로다`() {
            seedDashboardData(index = 1)
            val withOneOfEach = countStatements(batches = 1)

            (2..5).forEach { seedDashboardData(index = it) }
            val withFiveOfEach = countStatements(batches = 5)

            assertEquals(
                withOneOfEach,
                withFiveOfEach,
                "행마다 집계를 돌리면 데이터 수만큼 쿼리가 늘어난다 (1배치=$withOneOfEach, 5배치=$withFiveOfEach)",
            )
        }

        @Test
        fun `기간 필터를 걸어도 집계는 한 자릿수 쿼리로 끝난다`() {
            (1..5).forEach { seedDashboardData(index = it) }

            statistics.clear()
            summary("30d")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.metrics.newOrders.previousValue").value(0))
            val statements = statistics.prepareStatementCount

            assertTrue(statements in 1..9, "집계 쿼리 수가 예상 범위를 벗어났다: $statements")
        }

        /** 배치 하나가 결제완료 주문 2건(하나는 취소 요청 포함)·정산 1행·검수 대기 상품 1개를 심는다. */
        private fun seedDashboardData(index: Int) {
            seedOrder(status = OrderStatus.PAID)
            seedClaim(seedOrder(status = OrderStatus.PAID), ClaimType.CANCEL)
            seedSettlement(netAmount = 10_000L * index, status = SettlementStatus.PENDING)
            seedProduct(inspectionStatus = InspectionStatus.PENDING, stock = index)
        }

        /** 쿼리 수만 세면 조립이 터져 응답이 비어도 통과한다 — 응답 성공과 값을 함께 고정한다. */
        private fun countStatements(batches: Int): Long {
            statistics.clear()
            summary()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.metrics.newOrders.value").value(batches * 2))
                .andExpect(jsonPath("$.data.metrics.cancelRequests.value").value(batches))
                .andExpect(jsonPath("$.data.productStatuses.awaitingInspection").value(batches))
            return statistics.prepareStatementCount
        }
    }

    // ---------- 헬퍼 ----------

    private fun summary(
        period: String? = null,
        asToken: String = token,
    ): ResultActions =
        mockMvc.perform(
            get("/api/v1/sellers/me/dashboard/summary")
                .let { if (period == null) it else it.param("period", period) }
                .header(HttpHeaders.AUTHORIZATION, "Bearer $asToken"),
        )

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 0L)) }
    }

    private fun changeSellerStatus(
        userId: Long,
        status: SellerStatus,
    ) {
        tx.execute {
            em
                .createQuery("update Seller s set s.status = :status where s.userId = :userId")
                .setParameter("status", status)
                .setParameter("userId", userId)
                .executeUpdate()
        }
    }

    private fun seedProduct(
        sellerId: Long = sellerUserId,
        saleStatus: SaleStatus = SaleStatus.ON_SALE,
        inspectionStatus: InspectionStatus = InspectionStatus.APPROVED,
        stock: Int = 10,
    ): Long =
        tx.execute {
            val product = persistProduct(sellerId, "간식${System.nanoTime()}")
            em.persist(OptionCombination.create(product, "단품", 0L, stock, "sig${product.id}"))
            em.flush()
            em
                .createQuery("update Product p set p.saleStatus = :sale, p.inspectionStatus = :inspect where p.id = :id")
                .setParameter("sale", saleStatus)
                .setParameter("inspect", inspectionStatus)
                .setParameter("id", product.id)
                .executeUpdate()
            product.id
        }!!

    private fun deactivateCombinations(productId: Long) {
        tx.execute {
            em
                .createQuery("update OptionCombination c set c.isActive = false where c.product.id = :id")
                .setParameter("id", productId)
                .executeUpdate()
        }
    }

    private fun seedSettlement(
        netAmount: Long,
        status: SettlementStatus,
        sellerId: Long = sellerUserId,
    ) {
        tx.execute {
            val settlement =
                Settlement.create(
                    sellerId = sellerId,
                    orderId = ++settlementSeq,
                    orderItemId = ++settlementSeq,
                    grossAmount = netAmount,
                    shippingFee = 0L,
                    commission = 0L,
                    pgFee = 0L,
                    sellerCouponBurden = 0L,
                    platformFeeRate = BigDecimal("0.0800"),
                    pgFeeRate = BigDecimal("0.0280"),
                    confirmedOn = LocalDate.now(),
                    idempotencyKey = "SETTLE:ORDER_ITEM:${++settlementSeq}",
                )
            em.persist(settlement)
            em.flush()
            em
                .createQuery("update Settlement s set s.status = :status where s.id = :id")
                .setParameter("status", status)
                .setParameter("id", settlement.id)
                .executeUpdate()
        }
    }

    /** 취소 외 유형은 요청 경로가 아직 없어 취소로 만든 뒤 유형·상태·요청일을 쿼리로 옮긴다. */
    private fun seedClaim(
        orderId: Long,
        claimType: ClaimType,
        status: ClaimStatus = ClaimStatus.CLAIM_REQUESTED,
        requestedAt: LocalDateTime? = null,
    ) {
        tx.execute {
            val order = em.find(Order::class.java, orderId)
            val claim =
                Claim.request(
                    order = order,
                    claimType = ClaimType.CANCEL,
                    reasonCode = ClaimReasonCode.CHANGE_OF_MIND,
                    reasonDetail = null,
                    items = listOf(ClaimItem.of(order.items.first(), 1, 25_000L)),
                    autoApproveDueAt = LocalDateTime.now().plusDays(2),
                )
            em.persist(claim)
            em.flush()
            em
                .createQuery(
                    "update Claim c set c.claimType = :claimType, c.status = :status, c.requestedAt = :requestedAt where c.id = :id",
                ).setParameter("claimType", claimType)
                .setParameter("status", status)
                .setParameter("requestedAt", requestedAt ?: claim.requestedAt)
                .setParameter("id", claim.id)
                .executeUpdate()
        }
    }

    private fun seedOrder(
        status: OrderStatus,
        sellerId: Long = sellerUserId,
        orderedAt: LocalDateTime? = null,
    ): Long {
        val seeded =
            tx.execute {
                val product = persistProduct(sellerId, "주문상품${System.nanoTime()}")
                val group =
                    OrderGroup.create(
                        buyerId = buyerUserId,
                        deliveryAddressId = 0L,
                        deliveryAddress =
                            DeliveryAddressSnapshot(
                                receiverNameEnc = piiStringCodec.encrypt("홍길동"),
                                contactNumberEnc = piiStringCodec.encrypt("010-1234-5678"),
                                zipCode = "06236",
                                baseAddress = "서울 강남구 테헤란로 1",
                                detailAddress = "101동 1203호",
                                deliveryMemo = null,
                            ),
                        usedPoint = 0L,
                        totalProductAmount = 25_000L,
                        totalShippingFee = 0L,
                        idempotencyKey = "dashboard-${System.nanoTime()}",
                        expiresAt = LocalDateTime.now().plusMinutes(10),
                    )
                em.persist(group)
                val order =
                    Order.create(
                        orderGroup = group,
                        sellerId = sellerId,
                        sellerNameSnapshot = "애착상회$sellerId",
                        allocatedCouponDiscount = 0L,
                        sellerShippingFee = 0L,
                        baseShippingFeeSnapshot = null,
                        freeShippingThresholdSnapshot = null,
                        items =
                            listOf(
                                OrderItem.of(
                                    productId = product.id,
                                    optionCombinationId = 1L,
                                    optionNameSnapshot = "단품",
                                    quantity = 1,
                                    unitPriceSnapshot = 25_000L,
                                    discountAllocatedAmount = 0L,
                                    productVersionId = 1L,
                                ),
                            ),
                    )
                em.persist(order)
                em.flush()
                em
                    .createQuery("update Order o set o.status = :status where o.id = :id")
                    .setParameter("status", status)
                    .setParameter("id", order.id)
                    .executeUpdate()
                order.id to group.id
            }!!
        orderedAt?.let { overrideOrderedAt(seeded.second, it) }
        return seeded.first
    }

    /** 주문일은 생성 시각 콜백이 채우므로 네이티브 UPDATE로 바꾼다 — created_at은 JPA 매핑이 갱신을 막는다. */
    private fun overrideOrderedAt(
        groupId: Long,
        orderedAt: LocalDateTime,
    ) {
        tx.execute {
            em
                .createNativeQuery("update order_groups set created_at = ? where id = ?")
                .setParameter(1, orderedAt)
                .setParameter(2, groupId)
                .executeUpdate()
        }
    }

    private fun persistProduct(
        sellerId: Long,
        name: String,
    ): Product {
        val category = Category.create(null, 1, "분류$name", null, 1)
        em.persist(category)
        val product = Product.register(category, sellerId, name, null, THUMBNAIL_KEY, 25_000L, null, null, null)
        em.persist(product)
        em.flush()
        return product
    }

    private companion object {
        private const val THUMBNAIL_KEY = "media/product/1/thumb.webp"
    }
}
