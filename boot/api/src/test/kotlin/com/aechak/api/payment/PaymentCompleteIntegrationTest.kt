package com.aechak.api.payment

import com.aechak.api.support.FakePaymentGateway
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.application.payment.port.PaymentGatewayView
import com.aechak.domain.order.cart.Cart
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 결제 확정 API 통합. 포트원 조회 결과별 4분기 응답과 확정 트랜잭션(그룹 선점·셀러 주문 일괄·결제 승인 기록),
 * 3중 금액 대조, 멱등 재호출, 동시 확정 경쟁을 실 MySQL로 고정함.
 * 선점이 조건부 원자 UPDATE라 Testcontainers 실 DB가 전제(H2 금지).
 */
class PaymentCompleteIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var paymentGateway: FakePaymentGateway

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        paymentGateway.clear()
    }

    // ---------- 픽스처 ----------

    /** 확정은 그룹·주문·장바구니만 읽어 상품·셀러는 값 참조 id로만 심음. 장바구니엔 주문된 조합과 잔류 확인용 조합을 함께 담음 */
    private fun seedGroupWithOrders(
        buyerId: Long,
        productAmount: Long = 13_000L,
        expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(10),
    ): String =
        tx.execute {
            val group =
                OrderGroup.create(
                    buyerId = buyerId,
                    deliveryAddressId = 1L,
                    deliveryAddress = snapshot(),
                    usedPoint = 0L,
                    totalProductAmount = productAmount,
                    totalShippingFee = 0L,
                    idempotencyKey = "key-${UUID.randomUUID()}",
                    expiresAt = expiresAt,
                )
            em.persist(group)
            em.persist(
                Order.create(
                    orderGroup = group,
                    sellerId = 71L,
                    sellerNameSnapshot = "멍멍상회",
                    allocatedCouponDiscount = 0L,
                    sellerShippingFee = 0L,
                    items =
                        listOf(
                            OrderItem.of(
                                productId = 1L,
                                optionCombinationId = ORDERED_COMBO_ID,
                                quantity = 1,
                                unitPriceSnapshot = productAmount,
                                discountAllocatedAmount = 0L,
                                productVersionId = 1L,
                            ),
                        ),
                ),
            )
            val cart = Cart.create(buyerId)
            em.persist(cart)
            cart.addItem(ORDERED_COMBO_ID, 1)
            cart.addItem(REMAINING_COMBO_ID, 1)
            em.flush()
            group.publicId
        }!!

    private fun snapshot() =
        DeliveryAddressSnapshot(
            receiverNameEnc = "enc-name",
            contactNumberEnc = "enc-contact",
            zipCode = "12345",
            baseAddress = "서울시 애착구 멍냥로 1",
            detailAddress = null,
            deliveryMemo = null,
        )

    private fun view(
        status: PaymentGatewayStatus,
        paidAmount: Long = 13_000L,
        failureCode: String? = null,
    ) = PaymentGatewayView(
        status = status,
        totalAmount = 13_000L,
        paidAmount = paidAmount,
        pgTxId = if (status == PaymentGatewayStatus.PAID) "stub-tx" else null,
        paidAt = if (status == PaymentGatewayStatus.PAID) LocalDateTime.of(2026, 1, 1, 0, 0) else null,
        failureCode = failureCode,
    )

    private fun expirePastNow(publicId: String) {
        tx.execute {
            em
                .createQuery("update OrderGroup g set g.expiresAt = :past where g.publicId = :pid")
                .setParameter("past", LocalDateTime.now().minusMinutes(1))
                .setParameter("pid", publicId)
                .executeUpdate()
        }
    }

    private fun cancelGroup(publicId: String) {
        tx.execute {
            em
                .createQuery(
                    "update OrderGroup g set g.status = com.aechak.domain.order.group.enums.OrderGroupStatus.CANCELLED where g.publicId = :pid",
                ).setParameter("pid", publicId)
                .executeUpdate()
        }
    }

    // ---------- 조회 헬퍼 ----------

    private fun groupStatus(publicId: String): String =
        em
            .createQuery("select g.status from OrderGroup g where g.publicId = :pid", Any::class.java)
            .setParameter("pid", publicId)
            .singleResult
            .toString()

    private fun orderStatuses(publicId: String): List<String> =
        em
            .createQuery("select o.status from Order o where o.orderGroup.publicId = :pid", Any::class.java)
            .setParameter("pid", publicId)
            .resultList
            .map { it.toString() }

    private fun paymentRow(paymentId: String): Array<*> =
        em
            .createQuery(
                "select p.status, p.transactionId, p.realPaidAmount, p.failureCode " +
                    "from PaymentJpaEntity p where p.paymentId = :pid",
                Array<Any>::class.java,
            ).setParameter("pid", paymentId)
            .singleResult

    // CartItem은 부모 역참조가 없는 애그리거트 자식이라 Cart를 통째로 읽는다 — LAZY 컬렉션이라 트랜잭션 안에서
    private fun cartComboIds(buyerId: Long): List<Long> =
        tx.execute {
            em
                .createQuery("select c from Cart c where c.buyerId = :buyerId", Cart::class.java)
                .setParameter("buyerId", buyerId)
                .singleResult
                .items
                .map { it.optionCombinationId }
        }!!

    // ---------- HTTP 헬퍼 ----------

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun prepare201(
        publicId: String,
        token: String,
    ) {
        mockMvc
            .perform(
                post("/api/v1/order-groups/$publicId/payment/prepare")
                    .bearer(token)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("""{"method":"KAKAO_PAY"}"""),
            ).andExpect(status().isCreated)
    }

    private fun complete(
        publicId: String,
        token: String,
    ): MockHttpServletRequestBuilder = post("/api/v1/order-groups/$publicId/payment/complete").bearer(token)

    // ---------- 테스트 ----------

    @Test
    fun `승인된 결제를 확정하면 주문그룹과 셀러 주문이 결제완료로 전이된다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, token) // Fake는 사전등록된 건을 기본 PAID로 조회해 줌

        mockMvc
            .perform(complete(publicId, token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PAID"))
            .andExpect(jsonPath("$.data.orderGroupId").value(publicId))
            .andExpect(jsonPath("$.data.finalPaymentAmount").value(13000))

        assertEquals("PAID", groupStatus(publicId))
        assertEquals(listOf("PAID"), orderStatuses(publicId))
        val row = paymentRow(publicId)
        assertEquals("APPROVED", row[0].toString())
        assertEquals("fake-tx-$publicId", row[1])
        assertEquals(13_000L, (row[2] as Number).toLong())
        // 주문된 조합만 장바구니에서 걷히고 나머지는 남는다
        assertEquals(listOf(REMAINING_COMBO_ID), cartComboIds(buyerId))
    }

    @Test
    fun `결제 사이에 수량이 달라진 장바구니 항목은 지우지 않는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, token)
        // 결제창이 떠 있는 사이 같은 옵션을 다시 담아 수량이 변함 — 주문 수량(1)과 어긋난다
        tx.execute {
            em
                .createQuery("update CartItem ci set ci.quantity = 3 where ci.optionCombinationId = :combo")
                .setParameter("combo", ORDERED_COMBO_ID)
                .executeUpdate()
        }

        mockMvc.perform(complete(publicId, token)).andExpect(status().isOk)

        // 주문한 그대로가 아니면 남긴다 — 과잉 삭제로 사용자가 담은 내역을 잃지 않는다
        assertEquals(setOf(ORDERED_COMBO_ID, REMAINING_COMBO_ID), cartComboIds(buyerId).toSet())
    }

    @Test
    fun `확정 재호출은 멱등이다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, token)

        mockMvc.perform(complete(publicId, token)).andExpect(status().isOk)
        mockMvc
            .perform(complete(publicId, token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PAID"))

        assertEquals("PAID", groupStatus(publicId))
        assertEquals("APPROVED", paymentRow(publicId)[0].toString())
    }

    @Test
    fun `PG 실패면 기록만 남기고 주문은 재결제 창구로 남는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, token)
        paymentGateway.stub(publicId, view(PaymentGatewayStatus.FAILED, failureCode = "PG_DECLINED"))

        mockMvc
            .perform(complete(publicId, token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.failureCode").value("PG_DECLINED"))

        assertEquals("PENDING_PAYMENT", groupStatus(publicId)) // 실패는 상태 전이가 아니다 — 재결제 창구
        assertEquals(listOf("PENDING_PAYMENT"), orderStatuses(publicId))
        val row = paymentRow(publicId)
        assertEquals("FAILED", row[0].toString())
        assertEquals("PG_DECLINED", row[3])
        assertEquals(2, cartComboIds(buyerId).size) // 장바구니도 그대로
    }

    @Test
    fun `실패 후 재시도 성공은 승인으로 종결된다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, token)
        paymentGateway.stub(publicId, view(PaymentGatewayStatus.FAILED, failureCode = "PG_DECLINED"))
        mockMvc.perform(complete(publicId, token)).andExpect(status().isOk)

        paymentGateway.stub(publicId, view(PaymentGatewayStatus.PAID))
        mockMvc
            .perform(complete(publicId, token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PAID"))

        val row = paymentRow(publicId)
        assertEquals("APPROVED", row[0].toString())
        assertEquals(null, row[3]) // 승인으로 종결됐으니 실패 기록은 지워진다
        assertEquals("PAID", groupStatus(publicId))
    }

    @Test
    fun `결제 시도가 없으면 NOT_STARTED다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId) // prepare를 부르지 않음 — 결제 행 없음

        mockMvc
            .perform(complete(publicId, token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))

        assertEquals("PENDING_PAYMENT", groupStatus(publicId))
    }

    @Test
    fun `결제창만 열리고 미결제면 NOT_STARTED다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, token)
        paymentGateway.stub(publicId, view(PaymentGatewayStatus.READY))

        mockMvc
            .perform(complete(publicId, token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))

        assertEquals("PENDING_PAYMENT", groupStatus(publicId))
        assertEquals("PENDING", paymentRow(publicId)[0].toString())
    }

    @Test
    fun `승인 대기 중이면 IN_PROGRESS다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, token)
        paymentGateway.stub(publicId, view(PaymentGatewayStatus.PAY_PENDING))

        mockMvc
            .perform(complete(publicId, token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))

        assertEquals("PENDING_PAYMENT", groupStatus(publicId))
        assertEquals("PENDING", paymentRow(publicId)[0].toString())
    }

    @Test
    fun `실결제액이 다르면 확정도 실패 처리도 하지 않는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, token)
        paymentGateway.stub(publicId, view(PaymentGatewayStatus.PAID, paidAmount = 12_000L))

        mockMvc
            .perform(complete(publicId, token))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(60010))

        assertEquals("PENDING_PAYMENT", groupStatus(publicId))
        assertEquals("PENDING", paymentRow(publicId)[0].toString()) // 돈은 받았는데 금액이 이상한 사건 — 사람 확인 전까지 동결
    }

    @Test
    fun `남의 주문그룹은 확정할 수 없다`() {
        val buyerId = createActiveUser()
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, mintAccessToken(buyerId))

        mockMvc
            .perform(complete(publicId, mintAccessToken(createActiveUser())))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(60008))
    }

    @Test
    fun `범위 밖 결제 상태는 명시적으로 거절한다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, token)
        paymentGateway.stub(publicId, view(PaymentGatewayStatus.CANCELLED))

        mockMvc
            .perform(complete(publicId, token))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(60011))
    }

    @Test
    fun `취소된 주문그룹에 도착한 확정은 60012다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, token)
        cancelGroup(publicId)

        mockMvc
            .perform(complete(publicId, token))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(60012))
    }

    @Test
    fun `만료가 지나도 승인이 확인되면 뒤늦게 확정한다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, token)
        expirePastNow(publicId)

        mockMvc
            .perform(complete(publicId, token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("PAID"))

        assertEquals("PAID", groupStatus(publicId)) // 만료 여부가 아니라 선점이 심판 — 승인난 돈을 버리지 않는다
    }

    @Test
    fun `동시 확정 요청은 한 번만 전이되고 둘 다 확정 결과를 받는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, token)

        val results = raceTwo { complete(publicId, token) }

        results.forEach { assertEquals(200, it.first) }
        assertEquals("PAID", groupStatus(publicId))
        assertEquals("APPROVED", paymentRow(publicId)[0].toString())
        assertEquals(listOf("PAID"), orderStatuses(publicId))
    }

    // ---------- 동시 실행 헬퍼 ----------

    /** 요청 둘을 동시에 쏘고 (status, body) 쌍을 돌려줌. */
    private fun raceTwo(request: () -> MockHttpServletRequestBuilder): List<Pair<Int, String>> {
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val futures =
                List(2) {
                    pool.submit<Pair<Int, String>> {
                        val builder = request()
                        ready.countDown()
                        start.await()
                        val response = mockMvc.perform(builder).andReturn().response
                        response.status to response.getContentAsString(Charsets.UTF_8)
                    }
                }
            ready.await()
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
        }
    }

    companion object {
        private const val ORDERED_COMBO_ID = 501L
        private const val REMAINING_COMBO_ID = 999L
    }
}
