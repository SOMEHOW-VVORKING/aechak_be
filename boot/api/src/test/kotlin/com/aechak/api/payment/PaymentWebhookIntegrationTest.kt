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
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 결제 웹훅 수신 통합. 로그인 없이 열린 경로를 서명만으로 지키는지, 콜백과 같은 확정 결과를 내는지,
 * 재전송해도 결과가 같은지를 실 MySQL로 고정함.
 * 깨지면 위조 통보로 주문이 결제완료가 되거나, 사용자가 결제창에서 이탈했을 때 확정이 영영 안 된다.
 */
class PaymentWebhookIntegrationTest : IntegrationTestBase() {
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

    // ---------- 테스트 ----------

    @Test
    fun `서명이 유효한 승인 통보는 로그인 없이도 주문을 결제완료로 만든다`() {
        val buyerId = createActiveUser()
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, mintAccessToken(buyerId)) // Fake는 사전등록된 건을 PAID로 조회해 줌

        mockMvc.perform(webhook(paidBody(publicId))).andExpect(status().isOk)

        assertEquals("PAID", groupStatus(publicId))
        assertEquals(listOf("PAID"), orderStatuses(publicId))
        assertEquals("APPROVED", paymentStatus(publicId))
        assertEquals(emptyList<Long>(), cartComboIds(buyerId), "확정 뒤 주문한 항목은 장바구니에서 걷힌다")
    }

    @Test
    fun `서명이 틀린 통보는 401이고 주문 상태를 바꾸지 않는다`() {
        val buyerId = createActiveUser()
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, mintAccessToken(buyerId))
        val body = paidBody(publicId)

        mockMvc
            .perform(webhook(body).header(WEBHOOK_SIGNATURE, "v1,ZmFrZS1zaWduYXR1cmU="))
            .andExpect(status().isUnauthorized)

        assertEquals("PENDING_PAYMENT", groupStatus(publicId))
        assertEquals("PENDING", paymentStatus(publicId))
    }

    @Test
    fun `서명 헤더가 아예 없는 통보도 401이다`() {
        val publicId = seedGroupWithOrders(createActiveUser())

        mockMvc
            .perform(
                post(WEBHOOK_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(paidBody(publicId)),
            ).andExpect(status().isUnauthorized)

        assertEquals("PENDING_PAYMENT", groupStatus(publicId))
    }

    @Test
    fun `확정과 무관한 사건은 200으로 받고 아무것도 하지 않는다`() {
        val buyerId = createActiveUser()
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, mintAccessToken(buyerId))
        val body = paidBody(publicId).replace("Transaction.Paid", "Transaction.VirtualAccountIssued")

        mockMvc.perform(webhook(body)).andExpect(status().isOk)

        assertEquals("PENDING_PAYMENT", groupStatus(publicId))
        assertEquals("PENDING", paymentStatus(publicId))
    }

    @Test
    fun `모르는 결제의 통보는 200으로 접는다`() {
        // 재전송받아도 결론이 같다 — 실패로 돌려주면 포트원이 무의미한 재전송을 반복한다
        mockMvc.perform(webhook(paidBody("모르는-결제-1"))).andExpect(status().isOk)
    }

    @Test
    fun `콜백이 먼저 확정한 뒤 온 통보는 상태를 그대로 둔다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, token)
        mockMvc
            .perform(post("$BASE_PATH/order-groups/$publicId/payment/complete").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
        val approvedAt = paymentUpdatedAt(publicId)

        mockMvc.perform(webhook(paidBody(publicId))).andExpect(status().isOk)

        assertEquals("PAID", groupStatus(publicId))
        assertEquals("APPROVED", paymentStatus(publicId))
        assertEquals(approvedAt, paymentUpdatedAt(publicId), "이미 승인된 결제 행을 다시 쓰면 안 된다")
    }

    @Test
    fun `같은 통보를 두 번 받아도 결과가 같다`() {
        val buyerId = createActiveUser()
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, mintAccessToken(buyerId))
        val body = paidBody(publicId)

        mockMvc.perform(webhook(body)).andExpect(status().isOk)
        mockMvc.perform(webhook(body)).andExpect(status().isOk)

        assertEquals("PAID", groupStatus(publicId))
        assertEquals(listOf("PAID"), orderStatuses(publicId))
    }

    @Test
    fun `실패 통보는 결제 행에 실패를 기록하고 주문은 재결제 가능하게 남긴다`() {
        val buyerId = createActiveUser()
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, mintAccessToken(buyerId))
        paymentGateway.stub(
            publicId,
            PaymentGatewayView(
                status = PaymentGatewayStatus.FAILED,
                totalAmount = PRODUCT_AMOUNT,
                paidAmount = 0L,
                pgTxId = null,
                paidAt = null,
                failureCode = "CARD_LIMIT_EXCEEDED",
            ),
        )
        val body = paidBody(publicId).replace("Transaction.Paid", "Transaction.Failed")

        mockMvc.perform(webhook(body)).andExpect(status().isOk)

        assertEquals("PENDING_PAYMENT", groupStatus(publicId), "그룹은 재결제가 열려 있어야 한다")
        assertEquals("FAILED", paymentStatus(publicId))
        assertEquals("CARD_LIMIT_EXCEEDED", paymentFailureCode(publicId))
    }

    @Test
    fun `한글이 섞인 본문도 받은 원문 그대로 서명이 검증된다`() {
        // 서명은 wire 원문 기준 — 컨버터의 charset 처리가 바뀌면 비ASCII 본문에서 이 테스트가 먼저 깨진다
        val buyerId = createActiveUser()
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, mintAccessToken(buyerId))
        val body =
            """{"type":"Transaction.Paid","timestamp":"2026-08-28T00:00:00Z","memo":"애착 상점 — 한글 메모",""" +
                """"data":{"paymentId":"$publicId","transactionId":"tx-한글","storeId":"store-test-1"}}"""

        mockMvc.perform(webhook(body)).andExpect(status().isOk)

        assertEquals("PAID", groupStatus(publicId))
    }

    @Test
    fun `취소된 주문에 온 승인 통보는 200으로 접고 상태를 바꾸지 않는다`() {
        // 만료 배치가 먼저 취소한 뒤 승인이 들어온 경우. 돈은 포트원에 남아 있고 환불은 후속 작업이다
        val buyerId = createActiveUser()
        val publicId = seedGroupWithOrders(buyerId)
        prepare201(publicId, mintAccessToken(buyerId))
        cancelGroup(publicId)

        mockMvc.perform(webhook(paidBody(publicId))).andExpect(status().isOk)

        assertEquals("CANCELLED", groupStatus(publicId))
        assertEquals("PENDING", paymentStatus(publicId))
    }

    // ---------- 픽스처 ----------

    /** 확정은 그룹·주문·장바구니만 읽어 상품·셀러는 값 참조 id로만 심음 */
    private fun seedGroupWithOrders(buyerId: Long): String =
        tx.execute {
            val group =
                OrderGroup.create(
                    buyerId = buyerId,
                    deliveryAddressId = 1L,
                    deliveryAddress =
                        DeliveryAddressSnapshot(
                            receiverNameEnc = "enc-name",
                            contactNumberEnc = "enc-contact",
                            zipCode = "12345",
                            baseAddress = "서울시 애착구 멍냥로 1",
                            detailAddress = null,
                            deliveryMemo = null,
                        ),
                    usedPoint = 0L,
                    totalProductAmount = PRODUCT_AMOUNT,
                    totalShippingFee = 0L,
                    idempotencyKey = "key-${UUID.randomUUID()}",
                    expiresAt = LocalDateTime.now().plusMinutes(10),
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
                                unitPriceSnapshot = PRODUCT_AMOUNT,
                                discountAllocatedAmount = 0L,
                                productVersionId = 1L,
                            ),
                        ),
                ),
            )
            val cart = Cart.create(buyerId)
            em.persist(cart)
            cart.addItem(ORDERED_COMBO_ID, 1)
            em.flush()
            group.publicId
        }!!

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

    private fun paymentStatus(paymentId: String): String =
        em
            .createQuery("select p.status from PaymentJpaEntity p where p.paymentId = :pid", Any::class.java)
            .setParameter("pid", paymentId)
            .singleResult
            .toString()

    private fun paymentFailureCode(paymentId: String): String? =
        em
            .createQuery("select p.failureCode from PaymentJpaEntity p where p.paymentId = :pid", String::class.java)
            .setParameter("pid", paymentId)
            .singleResult

    private fun paymentUpdatedAt(paymentId: String): Any =
        em
            .createQuery("select p.updatedAt from PaymentJpaEntity p where p.paymentId = :pid", Any::class.java)
            .setParameter("pid", paymentId)
            .singleResult

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

    private fun prepare201(
        publicId: String,
        token: String,
    ) {
        mockMvc
            .perform(
                post("$BASE_PATH/order-groups/$publicId/payment/prepare")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"method":"KAKAO_PAY"}"""),
            ).andExpect(status().isCreated)
    }

    /** 실제 포트원이 보내는 모양 그대로 — 서명은 받은 본문 문자열 기준이라 여기서 함께 만든다 */
    private fun webhook(body: String): MockHttpServletRequestBuilder {
        val id = "whid-${UUID.randomUUID()}"
        val timestamp = Instant.now().epochSecond.toString()
        return post(WEBHOOK_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .header(WEBHOOK_ID, id)
            .header(WEBHOOK_TIMESTAMP, timestamp)
            .header(WEBHOOK_SIGNATURE, sign(id, timestamp, body))
    }

    private fun paidBody(paymentId: String): String =
        """{"type":"Transaction.Paid","timestamp":"2026-08-28T00:00:00Z",""" +
            """"data":{"paymentId":"$paymentId","transactionId":"tx-1","storeId":"store-test-1"}}"""

    private fun sign(
        id: String,
        timestamp: String,
        body: String,
    ): String {
        val key = Base64.getDecoder().decode(WEBHOOK_SECRET.removePrefix("whsec_"))
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return "v1,${Base64.getEncoder().encodeToString(mac.doFinal("$id.$timestamp.$body".toByteArray()))}"
    }

    companion object {
        private const val BASE_PATH = "/api/v1"
        private const val WEBHOOK_PATH = "$BASE_PATH/webhooks/portone"

        /** src/test/resources/application.properties의 payment.portone.webhook-secret과 같아야 한다 */
        private const val WEBHOOK_SECRET = "whsec_dGVzdC13ZWJob29rLXNlY3JldC1rZXktMTIzNDU2"

        private const val WEBHOOK_ID = "webhook-id"
        private const val WEBHOOK_TIMESTAMP = "webhook-timestamp"
        private const val WEBHOOK_SIGNATURE = "webhook-signature"

        private const val PRODUCT_AMOUNT = 13_000L
        private const val ORDERED_COMBO_ID = 501L
    }
}
