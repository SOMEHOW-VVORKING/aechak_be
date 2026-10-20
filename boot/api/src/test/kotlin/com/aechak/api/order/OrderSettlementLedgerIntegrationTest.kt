package com.aechak.api.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.api.support.SettlementFeePolicyFixture
import com.aechak.application.settlement.usecase.SettlementUseCase
import com.aechak.application.settlement.usecase.command.CreateSettlementLedgerCommand
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.ConfirmType
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.settlement.feepolicy.SettlementFeePolicy
import com.aechak.domain.settlement.settlement.Settlement
import com.aechak.domain.settlement.settlement.enums.SettlementStatus
import com.aechak.domain.settlement.settlement.policy.SettlementCalendar
import com.aechak.domain.user.point.PointTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 구매확정에서 만들어지는 정산 원장 통합. 깨지면 셀러에게 지급할 금액이 틀리거나,
 * 재확정·자동확정 재실행이 같은 주문품목으로 원장을 두 번 만들어 지급이 중복된다.
 * 금액 계산이 DECIMAL 반올림과 UNIQUE에 걸려 있어 Testcontainers 실 MySQL로 고정한다(H2 금지).
 */
class OrderSettlementLedgerIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var settlementUseCase: SettlementUseCase

    private lateinit var mockMvc: MockMvc
    private var buyerId = 0L
    private lateinit var token: String
    private var sig = 0

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        buyerId = createActiveUser()
        token = mintAccessToken(buyerId)
        seedGlobalSettlementFeePolicy()
    }

    @Test
    fun `확정하면 주문 품목마다 정산 행이 생기고 배송비 안분액의 합이 주문 배송비와 같다`() {
        // 10,000×2−500 = 19,500 / 3,500×3 = 10,500. 배송비 3,000은 두 품목에 1,500씩
        val fixture = seedOrder(lines = listOf(Line(10_000L, 2, 500L), Line(3_500L, 3)), shippingFee = 3_000L)

        mockMvc.perform(confirmRequest(fixture.orderPublicId)).andExpect(status().isNoContent)

        val settlements = settlements(fixture.orderId)
        assertEquals(2, settlements.size, "정산 행은 주문품목 수만큼이어야 한다")
        assertEquals(listOf(19_500L, 10_500L), settlements.map { it.grossAmount })
        assertEquals(listOf(1_500L, 1_500L), settlements.map { it.shippingFee })
        assertEquals(3_000L, settlements.sumOf { it.shippingFee }, "안분액의 합이 주문의 셀러 배송비와 달라지면 안 된다")
        assertEquals(
            fixture.orderItemIds.map { Settlement.idempotencyKeyOf(it) },
            settlements.map { it.idempotencyKey },
        )
        assertTrue(settlements.all { it.status == SettlementStatus.PENDING })
        assertTrue(settlements.all { it.orderId == fixture.orderId })
        assertTrue(settlements.all { it.sellerId == SELLER_USER_ID })
    }

    @Test
    fun `수수료는 상품금액 기준으로 계산되고 배송비에는 붙지 않는다`() {
        // 19,500 × 8% = 1,560 / × 2.8% = 546. 배송비 3,000이 기준에 들어가면 1,800 / 630이 된다
        val fixture = seedOrder(lines = listOf(Line(10_000L, 2, 500L)), shippingFee = 3_000L)

        mockMvc.perform(confirmRequest(fixture.orderPublicId)).andExpect(status().isNoContent)

        val settlement = settlements(fixture.orderId).single()
        assertEquals(1_560L, settlement.commission)
        assertEquals(546L, settlement.pgFee)
        assertEquals(BigDecimal("0.0800"), settlement.platformFeeRate, "요율은 스냅샷으로 박혀야 한다")
        assertEquals(BigDecimal("0.0280"), settlement.pgFeeRate)
        // 19,500 + 3,000 − 1,560 − 546 = 20,394
        assertEquals(20_394L, settlement.netAmount)
        assertEquals(0L, settlement.refundDeduction)
    }

    @Test
    fun `정산월과 지급 예정일은 구매확정일에서 파생된다`() {
        val fixture = seedOrder()

        mockMvc.perform(confirmRequest(fixture.orderPublicId)).andExpect(status().isNoContent)

        val confirmedOn = SettlementCalendar.settlementDateOf(findOrder(fixture.orderId).purchaseConfirmedAt!!)
        val settlement = settlements(fixture.orderId).single()
        assertEquals(Settlement.settlementMonthOf(confirmedOn), settlement.settlementMonth)
        assertEquals(confirmedOn.plusDays(Settlement.PAYOUT_DELAY_DAYS), settlement.expectedPayoutDate)
    }

    @Test
    fun `두 번 확정해도 정산 행은 품목당 하나다`() {
        val fixture = seedOrder(lines = listOf(Line(10_000L, 2, 500L), Line(3_500L, 3)))

        mockMvc.perform(confirmRequest(fixture.orderPublicId)).andExpect(status().isNoContent)
        val first = settlements(fixture.orderId)

        mockMvc
            .perform(confirmRequest(fixture.orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50008))

        val second = settlements(fixture.orderId)
        assertEquals(2, second.size, "재확정 시도가 원장을 늘리면 안 된다")
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun `원장이 이미 있는 품목은 건너뛰고 없는 품목만 만든다`() {
        val fixture = seedOrder(lines = listOf(Line(10_000L, 2, 500L), Line(3_500L, 3)), shippingFee = 3_000L)
        // 앞선 실행이 한 품목까지만 원장을 만들고 멈춘 상태를 심는다
        val planted = plantSettlement(fixture, orderItemId = fixture.orderItemIds.first())

        mockMvc.perform(confirmRequest(fixture.orderPublicId)).andExpect(status().isNoContent)

        val settlements = settlements(fixture.orderId)
        assertEquals(2, settlements.size)
        assertEquals(planted, settlements.first().id, "이미 있던 행을 다시 만들거나 덮으면 안 된다")
        assertEquals(999L, settlements.first().grossAmount, "심어 둔 금액이 그대로 남아야 한다")
        assertEquals(1_500L, settlements.last().shippingFee, "배송비 안분은 남은 품목만으로 다시 계산되면 안 된다")
    }

    @Test
    fun `자동확정으로 확정된 주문도 수동 확정과 같은 정산 행을 만든다`() {
        val manual = seedOrder(lines = listOf(Line(10_000L, 2, 500L), Line(3_500L, 3)), shippingFee = 3_000L)
        val auto = seedOrder(lines = listOf(Line(10_000L, 2, 500L), Line(3_500L, 3)), shippingFee = 3_000L)

        mockMvc.perform(confirmRequest(manual.orderPublicId)).andExpect(status().isNoContent)
        confirmAutomatically(auto)

        assertEquals(ConfirmType.AUTO, findOrder(auto.orderId).confirmType)
        assertEquals(amountsOf(settlements(manual.orderId)), amountsOf(settlements(auto.orderId)))

        // 자동확정 배치의 재실행도 행을 늘리지 않는다
        confirmAutomatically(auto)
        assertEquals(2, settlements(auto.orderId).size)
    }

    @Test
    fun `요율이 바뀌어도 이미 만들어진 정산 행의 요율과 금액은 그대로다`() {
        val before = seedOrder(lines = listOf(Line(10_000L, 2, 500L)))
        mockMvc.perform(confirmRequest(before.orderPublicId)).andExpect(status().isNoContent)
        val snapshot = amountsOf(settlements(before.orderId))

        seedFeePolicy(platformFeeRate = BigDecimal("0.1000"), pgFeeRate = BigDecimal("0.0300"))
        val after = seedOrder(lines = listOf(Line(10_000L, 2, 500L)))
        mockMvc.perform(confirmRequest(after.orderPublicId)).andExpect(status().isNoContent)

        assertEquals(snapshot, amountsOf(settlements(before.orderId)), "과거 정산이 새 요율로 바뀌면 안 된다")
        val fresh = settlements(after.orderId).single()
        assertEquals(BigDecimal("0.1000"), fresh.platformFeeRate)
        assertEquals(1_950L, fresh.commission, "19,500의 10%")
        assertEquals(585L, fresh.pgFee, "19,500의 3%")
    }

    @Test
    fun `셀러 전용 요율이 있으면 전역 요율 대신 그 요율이 박힌다`() {
        seedFeePolicy(sellerId = SELLER_USER_ID, platformFeeRate = BigDecimal("0.0500"), pgFeeRate = BigDecimal("0.0200"))
        val fixture = seedOrder(lines = listOf(Line(10_000L, 2, 500L)))

        mockMvc.perform(confirmRequest(fixture.orderPublicId)).andExpect(status().isNoContent)

        val settlement = settlements(fixture.orderId).single()
        assertEquals(BigDecimal("0.0500"), settlement.platformFeeRate)
        assertEquals(975L, settlement.commission, "19,500의 5%")
        assertEquals(390L, settlement.pgFee, "19,500의 2%")
    }

    @Test
    fun `동시 확정에서도 정산 행은 품목당 하나다`() {
        // 멱등키 선조회는 순차 재실행만 접는다 — 동시 호출을 막는 것은 주문 전이 선점이다
        val fixture = seedOrder(lines = listOf(Line(10_000L, 2, 500L), Line(3_500L, 3)), shippingFee = 3_000L)

        val results = raceTwoConfirms(fixture.orderPublicId)

        assertEquals(listOf(204, 409), results.sorted(), "하나만 확정되고 나머지는 이미 확정으로 거절돼야 한다")
        assertEquals(2, settlements(fixture.orderId).size)
    }

    @Test
    fun `요율 정책이 없으면 확정과 적립까지 함께 롤백된다`() {
        deleteFeePolicies()
        val fixture = seedOrder(lines = listOf(Line(10_000L, 2, 500L)))

        mockMvc
            .perform(confirmRequest(fixture.orderPublicId))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.errorCode").value(70005))

        val order = findOrder(fixture.orderId)
        assertEquals(OrderStatus.DELIVERED, order.status, "원장을 못 만들면 확정도 되지 않아야 한다")
        assertNull(order.purchaseConfirmedAt)
        assertEquals(0, settlements(fixture.orderId).size)
        assertEquals(0, rewardCount(fixture.orderPublicId), "적립 원장도 함께 롤백돼야 한다")
    }

    private fun confirmRequest(orderPublicId: String): MockHttpServletRequestBuilder =
        post("/api/v1/orders/$orderPublicId/confirm").header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    /** 같은 주문에 확정 요청 두 개를 함께 쏜다. 응답 상태만 돌려준다. */
    private fun raceTwoConfirms(orderPublicId: String): List<Int> {
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val futures =
                (1..2).map {
                    pool.submit<Int> {
                        ready.countDown()
                        start.await()
                        mockMvc
                            .perform(confirmRequest(orderPublicId))
                            .andReturn()
                            .response.status
                    }
                }
            ready.await()
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
        }
    }

    /** 자동확정 배치가 할 일을 그대로 흉내 낸다 — 확정 유형만 AUTO이고 원장 생성 입력은 수동 확정과 같다. */
    private fun confirmAutomatically(fixture: Fixture) {
        val command =
            tx.execute {
                val order = em.find(Order::class.java, fixture.orderId)
                if (order.status == OrderStatus.DELIVERED) {
                    order.confirmPurchase(ConfirmType.AUTO)
                    em.flush()
                }
                CreateSettlementLedgerCommand(
                    sellerId = order.sellerId,
                    orderId = order.id,
                    confirmedAt = order.purchaseConfirmedAt!!,
                    sellerShippingFee = order.sellerShippingFee,
                    items =
                        order.items.map {
                            CreateSettlementLedgerCommand.Item(orderItemId = it.id, grossAmount = it.productAmountAfterDiscount())
                        },
                )
            }!!
        settlementUseCase.createLedgerOnConfirm(command)
    }

    /** id·주문 식별자를 뺀 금액과 날짜만 비교한다 — 확정 경로가 달라도 같아야 하는 값들이다. */
    private fun amountsOf(settlements: List<Settlement>): List<String> =
        settlements.map {
            listOf(
                it.grossAmount,
                it.shippingFee,
                it.commission,
                it.pgFee,
                it.sellerCouponBurden,
                it.refundDeduction,
                it.netAmount,
                it.platformFeeRate,
                it.pgFeeRate,
                it.settlementMonth,
                it.expectedPayoutDate,
                it.status,
            ).joinToString("|")
        }

    private fun settlements(orderId: Long): List<Settlement> =
        tx.execute {
            em
                .createQuery(
                    "select s from Settlement s where s.orderId = :orderId order by s.orderItemId",
                    Settlement::class.java,
                ).setParameter("orderId", orderId)
                .resultList
        }!!

    private fun findOrder(orderId: Long): Order = tx.execute { em.find(Order::class.java, orderId) }!!

    private fun rewardCount(orderPublicId: String): Int =
        tx.execute {
            em
                .createQuery(
                    "select t from PointTransaction t where t.idempotencyKey = :key",
                    PointTransaction::class.java,
                ).setParameter("key", "EARN:PURCHASE:$orderPublicId")
                .resultList
                .size
        }!!

    private fun plantSettlement(
        fixture: Fixture,
        orderItemId: Long,
    ): Long =
        tx.execute {
            val settlement =
                Settlement.create(
                    sellerId = SELLER_USER_ID,
                    orderId = fixture.orderId,
                    orderItemId = orderItemId,
                    grossAmount = 999L,
                    shippingFee = 0L,
                    commission = 0L,
                    pgFee = 0L,
                    sellerCouponBurden = 0L,
                    platformFeeRate = BigDecimal("0.0800"),
                    pgFeeRate = BigDecimal("0.0280"),
                    confirmedOn = LocalDate.now(),
                    idempotencyKey = Settlement.idempotencyKeyOf(orderItemId),
                )
            em.persist(settlement)
            em.flush()
            settlement.id
        }!!

    private fun seedFeePolicy(
        sellerId: Long? = null,
        platformFeeRate: BigDecimal,
        pgFeeRate: BigDecimal,
    ) = tx.execute {
        // 적용 시작일이 더 늦은 행이 이긴다 — 베이스가 심은 2026-01-01 전역 정책을 덮는다
        em.persist(SettlementFeePolicy.of(sellerId, LocalDate.now().minusDays(1), platformFeeRate, pgFeeRate))
        em.flush()
    }

    private fun deleteFeePolicies() =
        tx.execute {
            em.createQuery("delete from SettlementFeePolicy").executeUpdate()
        }

    private fun seedOrder(
        lines: List<Line> = listOf(Line(10_000L, 2, 500L)),
        shippingFee: Long = 3_000L,
    ): Fixture =
        tx.execute {
            val tag = "s${sig++}"
            val sellerUserId = ensureSeller()
            val category = Category.create(null, 1, "카테고리-$tag", null, 1)
            em.persist(category)
            val items =
                lines.mapIndexed { index, line ->
                    val product =
                        Product.register(
                            category,
                            sellerUserId,
                            "사료-$tag-$index",
                            null,
                            "products/$tag-$index.jpg",
                            line.unitPrice,
                            null,
                            null,
                            null,
                        )
                    em.persist(product)
                    em.flush()
                    val version =
                        ProductVersion.snapshot(
                            product = product,
                            versionNo = 1,
                            nameSnapshot = "사료-$tag-$index",
                            priceSnapshot = line.unitPrice,
                            statusSnapshot = SaleStatus.ON_SALE,
                            thumbnailKeySnapshot = "products/$tag-$index.jpg",
                            changedBy = VersionChangedBy.SELLER,
                        )
                    em.persist(version)
                    val combination = OptionCombination.create(product, "블랙 / L", 0L, 100, "sig-$tag-$index")
                    em.persist(combination)
                    em.flush()
                    OrderItem.of(
                        productId = product.id,
                        optionCombinationId = combination.id,
                        optionNameSnapshot = combination.name,
                        quantity = line.quantity,
                        unitPriceSnapshot = line.unitPrice,
                        discountAllocatedAmount = line.discountAllocatedAmount,
                        productVersionId = version.id,
                    )
                }

            val group =
                OrderGroup.create(
                    buyerId = buyerId,
                    deliveryAddressId = 0L,
                    deliveryAddress = deliveryAddressSnapshot(),
                    usedPoint = 0L,
                    totalProductAmount = lines.sumOf { it.unitPrice * it.quantity },
                    totalShippingFee = shippingFee,
                    idempotencyKey = "idem-$tag-$buyerId",
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                )
            em.persist(group)
            val order =
                Order.create(
                    orderGroup = group,
                    sellerId = sellerUserId,
                    sellerNameSnapshot = "store-$sellerUserId",
                    allocatedCouponDiscount = 0L,
                    sellerShippingFee = shippingFee,
                    baseShippingFeeSnapshot = null,
                    freeShippingThresholdSnapshot = null,
                    items = items,
                )
            em.persist(order)
            em.flush()
            em
                .createQuery("update Order o set o.status = :status where o.id = :id")
                .setParameter("status", OrderStatus.DELIVERED)
                .setParameter("id", order.id)
                .executeUpdate()
            Fixture(
                orderPublicId = order.publicId,
                orderId = order.id,
                orderItemIds = order.items.map { it.id }.sorted(),
            )
        }!!

    private fun ensureSeller(): Long {
        if (em.find(Seller::class.java, SELLER_USER_ID) == null) {
            em.persist(Seller.open(userId = SELLER_USER_ID, storeName = "store-$SELLER_USER_ID", baseShippingFee = 3_000L))
            em.flush()
        }
        return SELLER_USER_ID
    }

    private fun deliveryAddressSnapshot() =
        DeliveryAddressSnapshot(
            receiverNameEnc = "enc-receiver",
            contactNumberEnc = "enc-contact",
            zipCode = "12345",
            baseAddress = "서울시 애착구 멍냥로 1",
            detailAddress = null,
            deliveryMemo = null,
        )

    private data class Line(
        val unitPrice: Long,
        val quantity: Int,
        val discountAllocatedAmount: Long = 0L,
    )

    private data class Fixture(
        val orderPublicId: String,
        val orderId: Long,
        val orderItemIds: List<Long>,
    )

    private companion object {
        private const val SELLER_USER_ID = 9_000_001L
    }
}
