package com.aechak.api.payment

import com.aechak.api.support.FakePaymentGateway
import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.payment.enums.PaymentMethod
import com.jayway.jsonpath.JsonPath
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
 * 결제 준비 API 통합. HTTP 경계부터 실 MySQL까지 태워 결제행 생성·재호출 멱등·사전등록 실패 복구와
 * 주문그룹 상태별 거절, 요청 결제수단의 행 반영을 고정함.
 * 동시 요청 한 건은 uk_payments_order_group_id가 심판이라 Testcontainers 실 DB가 전제(H2 금지).
 */
class PaymentPrepareIntegrationTest : IntegrationTestBase() {
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

    /** 결제 준비가 셀러·상품·장바구니를 안 읽어 주문그룹만 심음 */
    private fun seedOrderGroup(
        buyerId: Long,
        productAmount: Long = 13_000L,
        shippingFee: Long = 0L,
        expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(10),
        mutate: (OrderGroup) -> Unit = {},
    ): String =
        tx.execute {
            val group =
                OrderGroup.create(
                    buyerId = buyerId,
                    deliveryAddressId = 1L,
                    deliveryAddress = snapshot(),
                    usedPoint = 0L,
                    totalProductAmount = productAmount,
                    totalShippingFee = shippingFee,
                    idempotencyKey = "key-${UUID.randomUUID()}",
                    expiresAt = expiresAt,
                )
            mutate(group)
            em.persist(group)
            em.flush()
            group.publicId
        }!!

    private fun snapshot() =
        DeliveryAddressSnapshot(
            receiverNameEnc = "enc-name",
            contactNumberEnc = "enc-contact",
            zipCode = "12345",
            baseAddress = "서울시 애착구 멍냥로 1",
            detailAddress = "101동 202호",
            deliveryMemo = null,
        )

    /** 전역 집계로 세면 uk_payments_order_group_id가 아니라 DB 전체를 고정하게 됨 */
    private fun paymentCount(orderGroupPublicId: String): Long =
        em
            .createQuery(
                "select count(p) from PaymentJpaEntity p " +
                    "where p.orderGroupId = (select g.id from OrderGroup g where g.publicId = :pid)",
                java.lang.Long::class.java,
            ).setParameter("pid", orderGroupPublicId)
            .singleResult
            .toLong()

    private fun paymentRow(paymentId: String): Array<*> =
        em
            .createQuery(
                "select p.status, p.method, p.targetAmount " +
                    "from PaymentJpaEntity p where p.paymentId = :pid",
                Array<Any>::class.java,
            ).setParameter("pid", paymentId)
            .singleResult

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun prepare(
        publicId: String,
        token: String?,
        method: PaymentMethod = PaymentMethod.KAKAO_PAY,
    ): MockHttpServletRequestBuilder {
        val builder =
            post("/api/v1/order-groups/$publicId/payment/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"method":"$method"}""")
        return if (token != null) builder.bearer(token) else builder
    }

    private fun perform201(
        publicId: String,
        token: String,
    ): String =
        mockMvc
            .perform(prepare(publicId, token))
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    @Test
    fun `결제를 준비하면 결제행을 만들고 포트원에 금액을 사전등록한다`() {
        val buyerId = createActiveUser()
        val publicId = seedOrderGroup(buyerId, productAmount = 19_000L, shippingFee = 3_000L)

        val body =
            mockMvc
                .perform(prepare(publicId, mintAccessToken(buyerId)))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.targetAmount").value(22_000))
                .andExpect(jsonPath("$.data.storeId").value("store-test-1"))
                .andExpect(jsonPath("$.data.channelKey").value("channel-key-test-1"))
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertEquals(publicId, JsonPath.read<String>(body, "$.data.paymentId"), "paymentId는 주문그룹 publicId여야 한다")
        assertEquals(1L, paymentCount(publicId), "결제행이 1건 생겨야 한다")
        val row = paymentRow(publicId)
        assertEquals("PENDING", row[0].toString(), "준비 직후 상태는 PENDING이어야 한다")
        assertEquals("KAKAO_PAY", row[1].toString(), "요청한 결제수단이 결제행에 찍혀야 한다")
        assertEquals(22_000L, row[2], "targetAmount는 주문그룹 결제금액이어야 한다")
        assertEquals(22_000L, paymentGateway.registeredAmount(publicId), "포트원에 등록한 금액이 결제금액과 같아야 한다")
    }

    @Test
    fun `결제행을 재사용하면 요청 수단이 달라도 처음 저장한 수단이 남는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedOrderGroup(buyerId)

        mockMvc.perform(prepare(publicId, token, method = PaymentMethod.TOSS_PAY)).andExpect(status().isCreated)
        mockMvc.perform(prepare(publicId, token, method = PaymentMethod.CARD)).andExpect(status().isCreated)

        assertEquals("TOSS_PAY", paymentRow(publicId)[1].toString(), "재호출이 저장된 결제수단을 덮으면 안 된다")
    }

    @Test
    fun `같은 주문그룹으로 다시 준비하면 결제행은 재사용하고 사전등록은 다시 태운다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedOrderGroup(buyerId)

        val first = perform201(publicId, token)
        val second = perform201(publicId, token)

        assertEquals(
            JsonPath.read<String>(first, "$.data.paymentId"),
            JsonPath.read<String>(second, "$.data.paymentId"),
            "재호출은 같은 paymentId를 돌려줘야 한다",
        )
        assertEquals(1L, paymentCount(publicId), "재호출로 결제행이 늘면 안 된다")
        assertEquals(
            listOf(publicId, publicId),
            paymentGateway.preRegisteredPaymentIds,
            "행을 재사용해도 사전등록은 호출마다 다시 태워야 한다",
        )
    }

    @Test
    fun `사전등록이 실패하면 게이트웨이 오류로 끊기고 재호출이 같은 행으로 복구한다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedOrderGroup(buyerId)
        paymentGateway.failNextPreRegister()

        mockMvc
            .perform(prepare(publicId, token))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.errorCode").value(60005))

        assertEquals(1L, paymentCount(publicId), "사전등록 실패에도 결제행은 커밋돼 있어야 한다")

        perform201(publicId, token)

        assertEquals(1L, paymentCount(publicId), "복구 호출이 행을 새로 만들면 안 된다")
        assertEquals(13_000L, paymentGateway.registeredAmount(publicId), "복구 호출이 같은 금액을 다시 등록해야 한다")
    }

    @Test
    fun `남의 주문그룹은 없는 주문과 같은 코드로 거절한다`() {
        val ownerId = createActiveUser()
        val publicId = seedOrderGroup(ownerId)

        mockMvc
            .perform(prepare(publicId, mintAccessToken(createActiveUser())))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(60008))

        assertEquals(0L, paymentCount(publicId), "거절된 요청은 결제행을 만들면 안 된다")
    }

    @Test
    fun `없는 주문그룹이면 404로 거절한다`() {
        val buyerId = createActiveUser()

        mockMvc
            .perform(prepare("01AAAAAAAAAAAAAAAAAAAAAAAA", mintAccessToken(buyerId)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(60008))
    }

    @Test
    fun `취소된 주문그룹은 결제할 수 없다`() {
        val buyerId = createActiveUser()
        val publicId = seedOrderGroup(buyerId) { it.cancelUnpaid() }

        mockMvc
            .perform(prepare(publicId, mintAccessToken(buyerId)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(60009))

        assertEquals(0L, paymentCount(publicId), "거절된 요청은 결제행을 만들면 안 된다")
    }

    @Test
    fun `만료된 주문그룹은 결제할 수 없다`() {
        val buyerId = createActiveUser()
        val publicId = seedOrderGroup(buyerId, expiresAt = LocalDateTime.now().minusMinutes(1))

        mockMvc
            .perform(prepare(publicId, mintAccessToken(buyerId)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(60009))
            .andExpect(jsonPath("$.message").value("결제 가능 시간이 만료되었습니다."))
    }

    @Test
    fun `이미 결제된 주문그룹은 다시 결제할 수 없다`() {
        val buyerId = createActiveUser()
        val publicId = seedOrderGroup(buyerId) { it.markPaid() }

        mockMvc
            .perform(prepare(publicId, mintAccessToken(buyerId)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(60006))
    }

    @Test
    fun `결제금액이 0원이면 거절한다`() {
        val buyerId = createActiveUser()
        val publicId = seedOrderGroup(buyerId, productAmount = 0L, shippingFee = 0L)

        mockMvc
            .perform(prepare(publicId, mintAccessToken(buyerId)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(60007))

        assertEquals(0L, paymentCount(publicId), "거절된 요청은 결제행을 만들면 안 된다")
    }

    @Test
    fun `토큰이 없으면 401`() {
        val publicId = seedOrderGroup(createActiveUser())

        mockMvc
            .perform(prepare(publicId, token = null))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `동시 두 요청은 UNIQUE가 심판해 결제행 하나를 나눠 쓴다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId = seedOrderGroup(buyerId)

        val results = raceTwo { prepare(publicId, token) }

        results.forEach { assertEquals(201, it.first, "동시 요청 둘 다 201이어야 한다: ${it.second}") }
        assertEquals(
            JsonPath.read<String>(results[0].second, "$.data.paymentId"),
            JsonPath.read<String>(results[1].second, "$.data.paymentId"),
            "진 쪽도 같은 paymentId를 받아야 한다",
        )
        assertEquals(1L, paymentCount(publicId), "동시 요청으로 결제행이 둘이 되면 안 된다")
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
