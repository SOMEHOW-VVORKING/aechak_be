package com.aechak.seller.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimItem
import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
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
 * 셀러 클레임 승인·거부 통합. 승인은 상태만 바꾸고 환불은 집행하지 않는다 —
 * "승인은 됐는데 돈은 안 나갔다"가 refundStatus=NONE으로 드러나는 것이 이 PR의 계약이다.
 * 동시 승인의 심판은 @Version이라 Testcontainers 실 MySQL이 전제다(H2 금지).
 */
class SellerClaimDecisionIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String
    private var sig = 0

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
    fun `취소 클레임을 승인하면 204와 승인 상태가 되고 환불은 미집행으로 남는다`() {
        val seeded = seedClaim()

        mockMvc.perform(approveRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)

        val claim = claimOf(seeded.claimPublicId)
        assertEquals(ClaimStatus.APPROVED, claim.status)
        assertEquals(SellerApprovalStatus.APPROVED, claim.sellerApprovalStatus)
        assertEquals(RefundStatus.NONE, claim.refundStatus, "승인만으로 돈이 나가면 안 된다")
        assertTrue(claim.approvedAt != null)
        assertNull(claim.resolvedAt, "환불 전까지는 종결되지 않는다")
        assertEquals(OrderStatus.PAID, orderStatusOf(seeded.orderId), "환불 집행 전까지 주문은 취소되지 않는다")
    }

    @Test
    fun `승인 본문 없이도 취소 클레임은 승인된다`() {
        val seeded = seedClaim()

        mockMvc
            .perform(post("/api/v1/sellers/me/claims/${seeded.claimPublicId}/approve").bearer(token))
            .andExpect(status().isNoContent)

        assertEquals(ClaimStatus.APPROVED, claimOf(seeded.claimPublicId).status)
    }

    @Test
    fun `클레임을 거부하면 204와 사유가 남는다`() {
        val seeded = seedClaim()

        mockMvc
            .perform(rejectRequest(token, seeded.claimPublicId, """{"rejectionReason":"상품 하자가 확인되지 않습니다"}"""))
            .andExpect(status().isNoContent)

        val claim = claimOf(seeded.claimPublicId)
        assertEquals(ClaimStatus.REJECTED, claim.status)
        assertEquals(SellerApprovalStatus.REJECTED, claim.sellerApprovalStatus)
        assertEquals("상품 하자가 확인되지 않습니다", claim.rejectionReason)
        assertTrue(claim.resolvedAt != null)
    }

    @Test
    fun `거부 사유가 공백이면 400과 50405다`() {
        val seeded = seedClaim()

        mockMvc
            .perform(rejectRequest(token, seeded.claimPublicId, """{"rejectionReason":"   "}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(50405))

        assertEquals(ClaimStatus.CLAIM_REQUESTED, claimOf(seeded.claimPublicId).status)
    }

    @Test
    fun `타 셀러의 클레임 승인은 404와 50400이다`() {
        val seeded = seedClaim()
        val otherSeller = createActiveUser()
        openSeller(otherSeller)

        mockMvc
            .perform(approveRequest(mintAccessToken(otherSeller), seeded.claimPublicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50400))

        assertEquals(ClaimStatus.CLAIM_REQUESTED, claimOf(seeded.claimPublicId).status)
    }

    @Test
    fun `없는 클레임의 승인은 404와 50400이다`() {
        seedClaim()

        mockMvc
            .perform(approveRequest(token, "01JZZZZZZZZZZZZZZZZZZZZZZZ"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50400))
    }

    @Test
    fun `이미 승인된 클레임의 재승인은 409와 50408이다`() {
        val seeded = seedClaim()
        mockMvc.perform(approveRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)

        mockMvc
            .perform(approveRequest(token, seeded.claimPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50408))
    }

    @Test
    fun `거부된 클레임의 승인은 409와 50408이다`() {
        val seeded = seedClaim()
        mockMvc
            .perform(rejectRequest(token, seeded.claimPublicId, """{"rejectionReason":"하자 미확인"}"""))
            .andExpect(status().isNoContent)

        mockMvc
            .perform(approveRequest(token, seeded.claimPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50408))
    }

    @Test
    fun `정지된 셀러의 클레임 승인은 403과 50404다`() {
        val seeded = seedClaim()
        suspendSeller(sellerUserId)

        mockMvc
            .perform(approveRequest(token, seeded.claimPublicId))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(50404))

        assertEquals(ClaimStatus.CLAIM_REQUESTED, claimOf(seeded.claimPublicId).status)
    }

    @Test
    fun `미인증 클레임 승인은 401과 20004다`() {
        val seeded = seedClaim()

        mockMvc
            .perform(post("/api/v1/sellers/me/claims/${seeded.claimPublicId}/approve"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    /** 승인 경합의 심판은 @Version이다. 먼저 커밋한 쪽이 이기고 진 쪽은 낙관적 락(90003)이나 이미 처리됨(50408)을 받는다. */
    @Test
    fun `동시 승인은 하나만 성공한다`() {
        val seeded = seedClaim()

        val results = raceTwo { approveRequest(token, seeded.claimPublicId) }

        assertEquals(listOf(204, 409), results.map { it.first }.sorted(), "결과: $results")
        val loser = results.first { it.first == 409 }.second
        assertTrue(errorCodeOf(loser) in setOf("90003", "50408"), "경합에서 진 쪽은 90003이나 50408이어야 한다: $loser")
        val claim = claimOf(seeded.claimPublicId)
        assertEquals(ClaimStatus.APPROVED, claim.status)
        assertEquals(1, claim.version, "승인이 두 번 반영되면 버전이 2가 된다")
    }

    // ---------- 헬퍼 ----------

    private fun approveRequest(
        token: String,
        claimPublicId: String,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/sellers/me/claims/$claimPublicId/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")
            .bearer(token)

    private fun rejectRequest(
        token: String,
        claimPublicId: String,
        body: String,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/sellers/me/claims/$claimPublicId/reject")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .bearer(token)

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")

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

    private fun claimOf(claimPublicId: String): Claim =
        tx.execute {
            em
                .createQuery("select c from Claim c where c.publicId = :publicId", Claim::class.java)
                .setParameter("publicId", claimPublicId)
                .singleResult
        }!!

    private fun orderStatusOf(orderId: Long): OrderStatus =
        tx.execute {
            em
                .createQuery("select o.status from Order o where o.id = :id", OrderStatus::class.java)
                .setParameter("id", orderId)
                .singleResult
        }!!

    private data class SeededClaim(
        val claimPublicId: String,
        val orderId: Long,
    )

    /** 결제완료 주문 1건과 그 주문 전체를 대상으로 하는 셀러 응답 대기 클레임 1건을 심는다. */
    private fun seedClaim(): SeededClaim =
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
                    idempotencyKey = "claim-decision-${sig++}-${System.nanoTime()}",
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
            forceOrderStatus(order, OrderStatus.PAID)
            em.persist(order)
            em.flush()
            val claim =
                Claim.request(
                    order = order,
                    claimType = ClaimType.CANCEL,
                    reasonCode = ClaimReasonCode.CHANGE_OF_MIND,
                    reasonDetail = null,
                    items = listOf(ClaimItem.of(order.items.first(), quantity = 1, refundAmount = 25_000L)),
                    autoApproveDueAt = LocalDateTime.now().plusDays(2),
                )
            em.persist(claim)
            em.flush()
            SeededClaim(claimPublicId = claim.publicId, orderId = order.id)
        }!!

    /** 전이 API를 거치지 않고 상태를 심는다 — 주문 전이 규칙은 이 테스트의 대상이 아니다. */
    private fun forceOrderStatus(
        order: Order,
        status: OrderStatus,
    ) {
        Order::class.java
            .getDeclaredField("status")
            .apply { isAccessible = true }
            .set(order, status)
    }

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
