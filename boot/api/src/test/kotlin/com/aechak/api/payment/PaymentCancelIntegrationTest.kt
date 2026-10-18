package com.aechak.api.payment

import com.aechak.api.support.FakePaymentGateway
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.payment.port.PaymentCancelRequester
import com.aechak.application.payment.port.PaymentCancelStatus
import com.aechak.application.payment.usecase.PaymentUseCase
import com.aechak.application.payment.usecase.command.CancelPaymentCommand
import com.aechak.application.payment.usecase.result.CancelPaymentStatus
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.payment.error.PaymentErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
 * 결제 취소 통합. 이 PR은 엔드포인트를 만들지 않으므로 내부 포트를 직접 부른다(호출자는 클레임 환불 집행이다).
 * 고정하는 것: 원승인액 불변, 취소가능액만 차감, 부분취소 누적, 취소 선점의 배타성, 비동기 승인 시 원장 불변.
 * 선점이 @Version 낙관적 락이라 Testcontainers 실 MySQL이 전제다.
 */
class PaymentCancelIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var paymentGateway: FakePaymentGateway

    @Autowired
    private lateinit var paymentUseCase: PaymentUseCase

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

    @Test
    fun `전액 취소는 취소가능액만 0으로 깎고 원승인액은 그대로 둔다`() {
        val paid = paidGroup()

        val result = paymentUseCase.cancelPayment(cancelCommand(paid.orderGroupId, amount = null))

        assertEquals(CancelPaymentStatus.COMPLETED, result.status)
        assertEquals(PRODUCT_AMOUNT, result.cancelledAmount)
        val row = paymentRow(paid.publicId)
        assertEquals("CANCELLED", row.status)
        assertEquals(0L, row.cancellableAmount)
        assertEquals(PRODUCT_AMOUNT, row.cancelledAmount)
        assertEquals(PRODUCT_AMOUNT, row.targetAmount, "원승인액을 깎으면 대사가 모든 취소 건을 금액 불일치로 떨어뜨린다")
        assertEquals(PRODUCT_AMOUNT, row.realPaidAmount, "실결제액도 취소로 줄지 않는다")
        // 전액 취소는 금액을 싣지 않고, 중복 취소 방어용 취소가능액 대조값만 보낸다
        val sent = paymentGateway.cancelCommands.single()
        assertEquals(null, sent.amount)
        assertEquals(PRODUCT_AMOUNT, sent.currentCancellableAmount)
    }

    @Test
    fun `부분 취소를 두 번 받으면 부분취소를 거쳐 전액 취소로 종결된다`() {
        val paid = paidGroup()

        paymentUseCase.cancelPayment(cancelCommand(paid.orderGroupId, amount = 5_000L))
        val afterFirst = paymentRow(paid.publicId)
        val second = paymentUseCase.cancelPayment(cancelCommand(paid.orderGroupId, amount = 8_000L))

        assertEquals("PARTIAL_CANCELLED", afterFirst.status)
        assertEquals(8_000L, afterFirst.cancellableAmount)
        assertEquals(5_000L, afterFirst.cancelledAmount)
        val row = paymentRow(paid.publicId)
        assertEquals("CANCELLED", row.status, "잔액이 0이 되면 전액 취소로 종결돼야 한다")
        assertEquals(0L, row.cancellableAmount)
        assertEquals(PRODUCT_AMOUNT, row.cancelledAmount, "누적 취소액은 두 번의 합이어야 한다")
        assertEquals(8_000L, second.cancelledAmount)
        // 두 번째 요청의 대조값은 첫 취소가 반영된 잔액이어야 한다
        assertEquals(listOf(PRODUCT_AMOUNT, 8_000L), paymentGateway.cancelCommands.map { it.currentCancellableAmount })
    }

    @Test
    fun `취소가능액을 넘는 요청은 PG로 나가지 않고 60015다`() {
        val paid = paidGroup()

        val e =
            assertThrows(BusinessException::class.java) {
                paymentUseCase.cancelPayment(cancelCommand(paid.orderGroupId, amount = PRODUCT_AMOUNT + 1))
            }

        assertEquals(PaymentErrorCode.CANCEL_AMOUNT_EXCEEDS_CANCELLABLE, e.errorCode)
        assertEquals(0, paymentGateway.cancelCommands.size)
        assertEquals("APPROVED", paymentRow(paid.publicId).status, "거절된 요청이 선점을 남기면 재시도가 막힌다")
    }

    @Test
    fun `비동기 승인이면 원장을 깎지 않고 요청 상태로 남는다`() {
        val paid = paidGroup()
        paymentGateway.stubCancelStatus(paid.publicId, PaymentCancelStatus.REQUESTED)

        val result = paymentUseCase.cancelPayment(cancelCommand(paid.orderGroupId, amount = 5_000L))

        assertEquals(CancelPaymentStatus.REQUESTED, result.status)
        assertEquals(0L, result.cancelledAmount)
        val row = paymentRow(paid.publicId)
        assertEquals("CANCEL_REQUESTED", row.status)
        assertEquals(PRODUCT_AMOUNT, row.cancellableAmount, "PG도 아직 깎지 않았는데 미리 깎으면 대사가 금액 불일치 오보를 낸다")
        assertEquals(0L, row.cancelledAmount)
    }

    @Test
    fun `PG 오류로 끊기면 선점이 풀려 재시도할 수 있다`() {
        val paid = paidGroup()
        paymentGateway.stubCancelError(paid.publicId, PaymentErrorCode.PAYMENT_GATEWAY_ERROR)

        val e =
            assertThrows(BusinessException::class.java) {
                paymentUseCase.cancelPayment(cancelCommand(paid.orderGroupId, amount = 5_000L))
            }

        assertEquals(PaymentErrorCode.PAYMENT_GATEWAY_ERROR, e.errorCode)
        val row = paymentRow(paid.publicId)
        assertEquals("APPROVED", row.status, "응답 유실이면 재시도해야 하고, 중복 취소는 취소가능액 대조가 막는다")
        assertEquals(PRODUCT_AMOUNT, row.cancellableAmount)
    }

    @Test
    fun `선점이 커밋된 뒤 도착한 동시 취소는 60016으로 막힌다`() {
        val paid = paidGroup()
        val insideCancel = CountDownLatch(1)
        val release = CountDownLatch(1)
        // 첫 요청을 PG 호출 안에서 멈춰 세운다 — 이 시점에 취소 선점은 이미 커밋돼 있다
        paymentGateway.onCancel {
            insideCancel.countDown()
            release.await(10, TimeUnit.SECONDS)
        }

        val pool = Executors.newSingleThreadExecutor()
        try {
            val first = pool.submit { paymentUseCase.cancelPayment(cancelCommand(paid.orderGroupId, amount = 5_000L)) }
            insideCancel.await(10, TimeUnit.SECONDS)

            val loser =
                assertThrows(BusinessException::class.java) {
                    paymentUseCase.cancelPayment(cancelCommand(paid.orderGroupId, amount = 5_000L))
                }

            release.countDown()
            first.get(30, TimeUnit.SECONDS)
            assertEquals(PaymentErrorCode.PAYMENT_CANCEL_IN_PROGRESS, loser.errorCode)
        } finally {
            release.countDown()
            pool.shutdown()
        }

        val row = paymentRow(paid.publicId)
        assertEquals("PARTIAL_CANCELLED", row.status)
        assertEquals(5_000L, row.cancelledAmount, "한 번만 집행돼야 한다 — 두 번 깎이면 구매자에게 두 번 환불된다")
        assertEquals(8_000L, row.cancellableAmount)
        assertEquals(1, paymentGateway.cancelCommands.size, "진 쪽이 PG까지 나가면 중복 취소가 된다")
    }

    // ---------- 픽스처 ----------

    /** 그룹·주문을 심고 결제 준비·확정을 태워 승인된 결제 행을 만든다 */
    private fun paidGroup(): PaidGroup {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val publicId =
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
                        baseShippingFeeSnapshot = null,
                        freeShippingThresholdSnapshot = null,
                        items =
                            listOf(
                                OrderItem.of(
                                    productId = 1L,
                                    optionCombinationId = 501L,
                                    optionNameSnapshot = "기본",
                                    quantity = 1,
                                    unitPriceSnapshot = PRODUCT_AMOUNT,
                                    discountAllocatedAmount = 0L,
                                    productVersionId = 1L,
                                ),
                            ),
                    ),
                )
                em.flush()
                group.publicId
            }!!
        mockMvc
            .perform(
                post("/api/v1/order-groups/$publicId/payment/prepare")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"method":"KAKAO_PAY"}"""),
            ).andExpect(status().isCreated)
        mockMvc
            .perform(post("/api/v1/order-groups/$publicId/payment/complete").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
        val orderGroupId = orderGroupIdOf(publicId)
        return PaidGroup(publicId, orderGroupId)
    }

    private fun cancelCommand(
        orderGroupId: Long,
        amount: Long?,
    ) = CancelPaymentCommand(
        orderGroupId = orderGroupId,
        amount = amount,
        reason = "고객 변심",
        requester = PaymentCancelRequester.CUSTOMER,
    )

    private fun orderGroupIdOf(publicId: String): Long =
        em
            .createQuery("select g.id from OrderGroup g where g.publicId = :pid", java.lang.Long::class.java)
            .setParameter("pid", publicId)
            .singleResult
            .toLong()

    private fun paymentRow(paymentId: String): PaymentRow {
        val row =
            em
                .createQuery(
                    "select p.status, p.targetAmount, p.realPaidAmount, p.cancellableAmount, p.cancelledAmount " +
                        "from PaymentJpaEntity p where p.paymentId = :pid",
                    Array<Any>::class.java,
                ).setParameter("pid", paymentId)
                .singleResult
        em.clear() // 다음 조회가 1차 캐시가 아니라 커밋된 행을 보게 한다
        return PaymentRow(
            status = row[0].toString(),
            targetAmount = (row[1] as Number).toLong(),
            realPaidAmount = (row[2] as Number).toLong(),
            cancellableAmount = (row[3] as Number).toLong(),
            cancelledAmount = (row[4] as Number).toLong(),
        )
    }

    private data class PaidGroup(
        val publicId: String,
        val orderGroupId: Long,
    )

    private data class PaymentRow(
        val status: String,
        val targetAmount: Long,
        val realPaidAmount: Long,
        val cancellableAmount: Long,
        val cancelledAmount: Long,
    )

    companion object {
        private const val PRODUCT_AMOUNT = 13_000L
    }
}
