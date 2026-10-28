package com.aechak.seller.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.order.seller.usecase.SellerOrderUseCase
import com.aechak.application.order.seller.usecase.command.AcceptOrderCommand
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 셀러 발주 확인 통합. 전이의 심판이 조건부 UPDATE라 Testcontainers 실 MySQL이 전제다(H2 금지).
 * 전이 이력은 전이와 같은 트랜잭션에 들어가야 한다 — 기록 시각이 전이 시각이기 때문이다.
 */
class SellerOrderAcceptIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var sellerOrderUseCase: SellerOrderUseCase

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String

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
    }

    @Test
    fun `결제완료 주문을 발주 확인하면 204와 상품 준비중이 된다`() {
        val orderPublicId = seedOrder(OrderStatus.PAID)

        mockMvc.perform(acceptRequest(token, orderPublicId)).andExpect(status().isNoContent)

        assertEquals(OrderStatus.PREPARING, statusOf(orderPublicId))
    }

    @Test
    fun `발주 확인은 셀러가 바꿨다는 이력을 남긴다`() {
        val orderPublicId = seedOrder(OrderStatus.PAID)

        mockMvc.perform(acceptRequest(token, orderPublicId)).andExpect(status().isNoContent)

        val histories =
            tx.execute {
                em
                    .createQuery(
                        "select h from OrderStatusHistory h where h.order.publicId = :publicId",
                        OrderStatusHistory::class.java,
                    ).setParameter("publicId", orderPublicId)
                    .resultList
            }!!
        assertEquals(1, histories.size, "전이 1회에 이력 1건이어야 한다")
        val history = histories.first()
        assertEquals(OrderStatus.PAID, history.fromStatus)
        assertEquals(OrderStatus.PREPARING, history.toStatus)
        assertEquals(TriggeredBy.SELLER, history.triggeredBy)
    }

    @Test
    fun `이력은 전이와 같은 트랜잭션에 들어간다`() {
        val orderPublicId = seedOrder(OrderStatus.PAID)

        tx.execute { status ->
            sellerOrderUseCase.acceptOrder(AcceptOrderCommand(sellerId = sellerUserId, orderPublicId = orderPublicId))
            em.flush()
            em.clear()
            // 커밋 전에 둘이 함께 보여야 한다 — 이력이 커밋 뒤에 저장되면 여기서 0건이다
            assertEquals(OrderStatus.PREPARING, statusOf(orderPublicId))
            assertEquals(1, historyCount(orderPublicId))
            status.setRollbackOnly()
        }

        // 되돌리면 둘이 함께 사라져야 한다 — 이력이 별도 트랜잭션이면 전이만 사라진다
        assertEquals(OrderStatus.PAID, statusOf(orderPublicId))
        assertEquals(0, historyCount(orderPublicId), "전이가 되돌려졌는데 이력이 남으면 안 된다")
    }

    @Test
    fun `정지된 셀러의 발주 확인은 403과 50003이다`() {
        val orderPublicId = seedOrder(OrderStatus.PAID)
        suspendSeller(sellerUserId)

        mockMvc
            .perform(acceptRequest(token, orderPublicId))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(50003))

        assertEquals(OrderStatus.PAID, statusOf(orderPublicId))
    }

    @Test
    fun `이미 발주 확인된 주문은 409와 50004다`() {
        val orderPublicId = seedOrder(OrderStatus.PREPARING)

        mockMvc
            .perform(acceptRequest(token, orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50004))

        assertEquals(0, historyCount(orderPublicId), "거절된 요청이 이력을 남기면 안 된다")
    }

    @Test
    fun `결제대기 주문은 409와 50004다`() {
        val orderPublicId = seedOrder(OrderStatus.PENDING_PAYMENT)

        mockMvc
            .perform(acceptRequest(token, orderPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50004))

        assertEquals(OrderStatus.PENDING_PAYMENT, statusOf(orderPublicId))
    }

    @Test
    fun `타 셀러 주문의 발주 확인은 404와 50000이다`() {
        val orderPublicId = seedOrder(OrderStatus.PAID)
        val otherSeller = createActiveUser()
        openSeller(otherSeller)

        mockMvc
            .perform(acceptRequest(mintAccessToken(otherSeller), orderPublicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))

        assertEquals(OrderStatus.PAID, statusOf(orderPublicId), "거절된 요청이 상태를 바꾸면 안 된다")
    }

    @Test
    fun `없는 주문의 발주 확인은 404와 50000이다`() {
        seedOrder(OrderStatus.PAID)

        mockMvc
            .perform(acceptRequest(token, "01JZZZZZZZZZZZZZZZZZZZZZZZ"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    @Test
    fun `셀러가 아닌 계정의 발주 확인은 403과 10004다`() {
        val orderPublicId = seedOrder(OrderStatus.PAID)
        val notSeller = createActiveUser()

        mockMvc
            .perform(acceptRequest(mintAccessToken(notSeller), orderPublicId))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(10004))

        assertEquals(OrderStatus.PAID, statusOf(orderPublicId))
    }

    @Test
    fun `미인증 발주 확인은 401과 20004다`() {
        val orderPublicId = seedOrder(OrderStatus.PAID)

        mockMvc
            .perform(post("/api/v1/sellers/me/orders/$orderPublicId/accept"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    /** 두 트랜잭션이 같은 상태를 읽는 지점까지는 HTTP 경계에서 맞출 수 없어, 겹친 요청의 결과만 고정한다. */
    @Test
    fun `동시 발주 확인은 하나만 성공한다`() {
        val orderPublicId = seedOrder(OrderStatus.PAID)

        val results = raceTwo { acceptRequest(token, orderPublicId) }

        assertEquals(listOf(204, 409), results.map { it.first }.sorted(), "결과: $results")
        val loser = results.first { it.first == 409 }.second
        assertEquals("50004", errorCodeOf(loser), "경합에서 진 쪽은 50004여야 한다: $loser")
        assertEquals(OrderStatus.PREPARING, statusOf(orderPublicId))
        assertEquals(1, historyCount(orderPublicId), "성공한 전이 하나만 이력을 남겨야 한다")
    }

    // ---------- 헬퍼 ----------

    private fun acceptRequest(
        token: String,
        orderPublicId: String,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/sellers/me/orders/$orderPublicId/accept")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun errorCodeOf(body: String): String? = Regex("\"errorCode\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 0L)) }
    }

    private fun suspendSeller(userId: Long) {
        tx.execute {
            em
                .createQuery("update Seller s set s.status = :status where s.userId = :userId")
                .setParameter("status", SellerStatus.SUSPENDED)
                .setParameter("userId", userId)
                .executeUpdate()
        }
    }

    /** 주문그룹 1건 + 셀러 주문 1건을 심고 주문 공개 식별자를 돌려준다. */
    private fun seedOrder(status: OrderStatus): String =
        tx.execute {
            val group =
                OrderGroup.create(
                    buyerId = 9_999L,
                    deliveryAddressId = 0L,
                    deliveryAddress =
                        DeliveryAddressSnapshot(
                            receiverNameEnc = "enc-receiver",
                            contactNumberEnc = "enc-contact",
                            zipCode = "12345",
                            baseAddress = "서울시 애착구 멍냥로 1",
                            detailAddress = null,
                            deliveryMemo = null,
                        ),
                    usedPoint = 0L,
                    totalProductAmount = 25_000L,
                    totalShippingFee = 0L,
                    idempotencyKey = "accept-${status.name}-${System.nanoTime()}",
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                )
            em.persist(group)
            val order =
                Order.create(
                    orderGroup = group,
                    sellerId = sellerUserId,
                    sellerNameSnapshot = "애착상회$sellerUserId",
                    allocatedCouponDiscount = 0L,
                    sellerShippingFee = 0L,
                    baseShippingFeeSnapshot = null,
                    freeShippingThresholdSnapshot = null,
                    items =
                        listOf(
                            OrderItem.of(
                                productId = 1L,
                                optionCombinationId = 2L,
                                optionNameSnapshot = "기본 / 1개",
                                quantity = 1,
                                unitPriceSnapshot = 25_000L,
                                discountAllocatedAmount = 0L,
                                productVersionId = 3L,
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
            order.publicId
        }!!

    private fun statusOf(orderPublicId: String): OrderStatus =
        tx.execute {
            em
                .createQuery("select o.status from Order o where o.publicId = :publicId", OrderStatus::class.java)
                .setParameter("publicId", orderPublicId)
                .singleResult
        }!!

    private fun historyCount(orderPublicId: String): Int =
        tx.execute {
            em
                .createQuery(
                    "select count(h) from OrderStatusHistory h where h.order.publicId = :publicId",
                    Long::class.javaObjectType,
                ).setParameter("publicId", orderPublicId)
                .singleResult
                .toInt()
        }!!

    private fun raceTwo(request: () -> MockHttpServletRequestBuilder): List<Pair<Int, String>> {
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val futures =
                listOf(request(), request()).map { builder ->
                    pool.submit<Pair<Int, String>> {
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
}
