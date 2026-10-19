package com.aechak.application.payment.facade

import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.command.CreateOrderGroupCommand
import com.aechak.application.order.usecase.result.ConfirmGroupPaidResult
import com.aechak.application.order.usecase.result.CreateOrderGroupResult
import com.aechak.application.order.usecase.result.OrderItemForReviewResult
import com.aechak.application.payment.port.PaymentCancelCommand
import com.aechak.application.payment.port.PaymentCancelRequester
import com.aechak.application.payment.port.PaymentCancelResult
import com.aechak.application.payment.port.PaymentCancelStatus
import com.aechak.application.payment.port.PaymentGatewayPort
import com.aechak.application.payment.port.PaymentGatewayView
import com.aechak.application.payment.port.PaymentWebhookNotification
import com.aechak.application.payment.port.PaymentWebhookRequest
import com.aechak.application.payment.port.PaymentWebhookVerifier
import com.aechak.application.payment.service.PaymentService
import com.aechak.application.payment.usecase.command.CancelPaymentCommand
import com.aechak.application.payment.usecase.command.PreparePaymentCommand
import com.aechak.application.payment.usecase.result.CancelPaymentStatus
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.repository.OrderGroupRepository
import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.enums.PaymentStatus
import com.aechak.domain.payment.error.PaymentErrorCode
import com.aechak.domain.payment.repository.PaymentRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 계약 테스트. 결제행 저장에서 동시 요청에 UNIQUE로 지면 먼저 커밋된 행을 다시 읽어 그 행으로 사전등록함을 고정함.
 * 통합의 동시 요청 테스트는 두 요청이 실제로 충돌하지 않아도 통과해서 이 재조회 경로를 확정적으로 태우지 못함.
 * 깨지면 동시에 결제를 준비할 때 진 쪽이 500을 받음.
 */
class PaymentFacadeTest {
    private val orderGroups = FakeOrderGroupRepository()
    private val payments = FakePaymentRepository()
    private val gateway = RecordingPaymentGateway()
    private val facade =
        PaymentFacade(
            PaymentService(payments, orderGroups),
            gateway,
            UnusedWebhookVerifier(),
            UnusedOrderUseCase(),
            NoOpTransactionManager(),
        )

    @Test
    fun `저장에서 UNIQUE 경쟁에 지면 먼저 커밋된 결제행을 다시 읽어 그 행으로 사전등록한다`() {
        val group = orderGroups.add(orderGroup())
        val winner = Payment.prepare(group.id, group.publicId, PaymentMethod.TOSS_PAY, group.finalPaymentAmount)
        payments.loseNextSaveTo(winner)

        val result = facade.preparePayment(PreparePaymentCommand(BUYER_ID, group.publicId, PaymentMethod.CARD))

        assertEquals(group.publicId, result.paymentId, "진 쪽도 먼저 생긴 결제행의 paymentId를 받아야 한다")
        assertEquals(listOf(group.publicId to 13_000L), gateway.registered, "다시 읽은 행의 금액으로 한 번 사전등록해야 한다")
    }

    @Test
    fun `전액 취소는 금액을 싣지 않고 취소가능액 대조값만 보낸다`() {
        val group = orderGroups.add(orderGroup())
        payments.add(approvedPayment(group))

        val result = facade.cancelPayment(cancelCommand(group.id, amount = null))

        val sent = gateway.cancelCommands.single()
        assertNull(sent.amount, "전액 취소는 금액을 싣지 않아야 포트원이 전액으로 처리한다")
        assertEquals(13_000L, sent.currentCancellableAmount, "멱등키가 없어 이 대조값이 유일한 중복 취소 방어다")
        assertEquals(CancelPaymentStatus.COMPLETED, result.status)
        assertEquals(0L, result.cancellableAmount)
        assertEquals(PaymentStatus.CANCELLED, result.paymentStatus)
    }

    @Test
    fun `부분 취소는 요청 금액을 싣고 원승인액을 깎지 않는다`() {
        val group = orderGroups.add(orderGroup())
        payments.add(approvedPayment(group))

        val result = facade.cancelPayment(cancelCommand(group.id, amount = 5_000L))

        assertEquals(5_000L, gateway.cancelCommands.single().amount)
        assertEquals(PaymentStatus.PARTIAL_CANCELLED, result.paymentStatus)
        assertEquals(8_000L, result.cancellableAmount)
        val stored = payments.findByOrderGroupId(group.id)!!
        assertEquals(13_000L, stored.targetAmount, "원승인액을 깎으면 대사가 모든 취소 건을 금액 불일치로 떨어뜨린다")
        assertEquals(13_000L, stored.realPaidAmount)
    }

    @Test
    fun `PG가 접수만 하면 원장을 깎지 않고 요청 상태로 남긴다`() {
        val group = orderGroups.add(orderGroup())
        payments.add(approvedPayment(group))
        gateway.cancelStatus = PaymentCancelStatus.REQUESTED

        val result = facade.cancelPayment(cancelCommand(group.id, amount = 5_000L))

        assertEquals(CancelPaymentStatus.REQUESTED, result.status)
        assertEquals(0L, result.cancelledAmount)
        val stored = payments.findByOrderGroupId(group.id)!!
        assertEquals(PaymentStatus.CANCEL_REQUESTED, stored.status)
        assertEquals(13_000L, stored.cancellableAmount, "PG도 아직 깎지 않았는데 미리 깎으면 대사가 금액 불일치 오보를 낸다")
    }

    @Test
    fun `PG가 실패로 답하면 선점을 풀고 60018을 올린다`() {
        val group = orderGroups.add(orderGroup())
        payments.add(approvedPayment(group))
        gateway.cancelStatus = PaymentCancelStatus.FAILED

        val e = assertFailsWith<BusinessException> { facade.cancelPayment(cancelCommand(group.id, amount = 5_000L)) }

        assertEquals(PaymentErrorCode.PAYMENT_CANCEL_FAILED, e.errorCode)
        assertEquals(PaymentStatus.APPROVED, payments.findByOrderGroupId(group.id)!!.status, "선점이 남으면 재시도가 영구히 막힌다")
    }

    @Test
    fun `PG 호출이 예외로 끊기면 선점을 풀고 원래 예외를 그대로 올린다`() {
        val group = orderGroups.add(orderGroup())
        payments.add(approvedPayment(group))
        gateway.cancelError = PaymentErrorCode.PAYMENT_GATEWAY_ERROR

        val e = assertFailsWith<BusinessException> { facade.cancelPayment(cancelCommand(group.id, amount = 5_000L)) }

        assertEquals(PaymentErrorCode.PAYMENT_GATEWAY_ERROR, e.errorCode)
        val stored = payments.findByOrderGroupId(group.id)!!
        assertEquals(PaymentStatus.APPROVED, stored.status, "응답 유실이면 재시도해야 하고, 중복 취소는 취소가능액 대조가 막는다")
        assertEquals(13_000L, stored.cancellableAmount)
    }

    @Test
    fun `취소가능액을 넘는 요청은 PG로 나가지 않는다`() {
        val group = orderGroups.add(orderGroup())
        payments.add(approvedPayment(group))

        val e = assertFailsWith<BusinessException> { facade.cancelPayment(cancelCommand(group.id, amount = 13_001L)) }

        assertEquals(PaymentErrorCode.CANCEL_AMOUNT_EXCEEDS_CANCELLABLE, e.errorCode)
        assertEquals(0, gateway.cancelCommands.size, "우리 원장에서 걸러야 PG 호출 횟수 상한을 낭비하지 않는다")
    }

    @Test
    fun `선점 저장이 낙관적 락에 지면 60016이다`() {
        val group = orderGroups.add(orderGroup())
        payments.add(approvedPayment(group))
        payments.failNextSaveWithOptimisticLock()

        val e = assertFailsWith<BusinessException> { facade.cancelPayment(cancelCommand(group.id, amount = 5_000L)) }

        assertEquals(PaymentErrorCode.PAYMENT_CANCEL_IN_PROGRESS, e.errorCode, "동시 취소에서 진 쪽은 진행 중인 취소를 기다려야 한다")
        assertEquals(0, gateway.cancelCommands.size)
    }

    private fun approvedPayment(group: OrderGroup) =
        Payment
            .prepare(group.id, group.publicId, PaymentMethod.CARD, group.finalPaymentAmount)
            .approve(pgTxId = "pg-tx", realPaidAmount = group.finalPaymentAmount)

    private fun cancelCommand(
        orderGroupId: Long,
        amount: Long?,
    ) = CancelPaymentCommand(
        orderGroupId = orderGroupId,
        amount = amount,
        reason = "고객 변심",
        requester = PaymentCancelRequester.CUSTOMER,
    )

    private fun orderGroup() =
        OrderGroup.create(
            buyerId = BUYER_ID,
            deliveryAddressId = 1L,
            deliveryAddress =
                DeliveryAddressSnapshot(
                    receiverNameEnc = "enc-name",
                    contactNumberEnc = "enc-contact",
                    zipCode = "12345",
                    baseAddress = "서울시 애착구 멍냥로 1",
                    detailAddress = "101동 202호",
                    deliveryMemo = null,
                ),
            usedPoint = 0L,
            totalProductAmount = 13_000L,
            totalShippingFee = 0L,
            idempotencyKey = "key-1",
            expiresAt = LocalDateTime.now().plusMinutes(10),
        )

    companion object {
        private const val BUYER_ID = 7L
    }
}

private class FakeOrderGroupRepository : OrderGroupRepository {
    private val byPublicId = mutableMapOf<String, OrderGroup>()

    fun add(group: OrderGroup): OrderGroup = group.also { byPublicId[it.publicId] = it }

    override fun save(orderGroup: OrderGroup): OrderGroup = add(orderGroup)

    override fun findByIdempotencyKey(idempotencyKey: String): OrderGroup? = byPublicId.values.find { it.idempotencyKey == idempotencyKey }

    override fun findByPublicId(publicId: String): OrderGroup? = byPublicId[publicId]

    override fun findById(id: Long): OrderGroup? = byPublicId.values.find { it.id == id }

    override fun markPaidIfPending(id: Long): Boolean = throw UnsupportedOperationException("결제 준비 테스트에서는 호출되지 않는다")

    override fun cancelIfPending(id: Long): Boolean = throw UnsupportedOperationException("결제 준비 테스트에서는 호출되지 않는다")

    override fun findExpiredPending(
        now: LocalDateTime,
        afterExpiresAt: LocalDateTime?,
        afterId: Long?,
        limit: Int,
    ): List<OrderGroup> = throw UnsupportedOperationException("결제 준비 테스트에서는 호출되지 않는다")
}

/** 결제 준비 흐름은 주문 전이를 부르지 않는다 — 호출되면 테스트 전제가 깨진 것 */
private class UnusedOrderUseCase : OrderUseCase {
    override fun createOrderGroup(command: CreateOrderGroupCommand): CreateOrderGroupResult = throw UnsupportedOperationException()

    override fun getOrderItemForReview(
        orderItemId: Long,
        buyerId: Long,
    ): OrderItemForReviewResult? = throw UnsupportedOperationException()

    override fun confirmGroupPaid(orderGroupId: Long): ConfirmGroupPaidResult = throw UnsupportedOperationException()

    override fun clearOrderedCartItems(
        buyerId: Long,
        orderGroupId: Long,
    ): Int = throw UnsupportedOperationException()
}

private class FakePaymentRepository : PaymentRepository {
    private val byOrderGroupId = mutableMapOf<Long, Payment>()
    private var competitor: Payment? = null
    private var optimisticLockOnce = false

    fun add(payment: Payment): Payment = payment.also { byOrderGroupId[it.orderGroupId] = it }

    /** 다음 save 한 번은 경쟁 요청이 먼저 커밋한 것처럼 winner를 남기고 UNIQUE 위반을 던짐 */
    fun loseNextSaveTo(winner: Payment) {
        competitor = winner
    }

    /** 다음 save 한 번은 같은 행을 먼저 선점한 요청에 진 것처럼 버전 충돌을 던짐 */
    fun failNextSaveWithOptimisticLock() {
        optimisticLockOnce = true
    }

    override fun save(payment: Payment): Payment {
        if (optimisticLockOnce) {
            optimisticLockOnce = false
            throw OptimisticLockingFailureException("payments version")
        }
        competitor?.let {
            competitor = null
            byOrderGroupId[it.orderGroupId] = it
            throw DataIntegrityViolationException("uk_payments_order_group_id")
        }
        byOrderGroupId[payment.orderGroupId] = payment
        return payment
    }

    override fun findByOrderGroupId(orderGroupId: Long): Payment? = byOrderGroupId[orderGroupId]

    override fun findByPaymentId(paymentId: String): Payment? = byOrderGroupId.values.find { it.paymentId == paymentId }

    override fun findPaymentIdsUpdatedBetween(
        from: LocalDateTime,
        until: LocalDateTime,
    ): List<String> = byOrderGroupId.values.map { it.paymentId }
}

/** 결제 준비 흐름은 웹훅을 받지 않는다 — 호출되면 테스트 전제가 깨진 것 */
private class UnusedWebhookVerifier : PaymentWebhookVerifier {
    override fun verify(request: PaymentWebhookRequest): PaymentWebhookNotification? = throw UnsupportedOperationException()
}

private class RecordingPaymentGateway : PaymentGatewayPort {
    val registered = mutableListOf<Pair<String, Long>>()
    val cancelCommands = mutableListOf<PaymentCancelCommand>()
    var cancelStatus: PaymentCancelStatus = PaymentCancelStatus.SUCCEEDED
    var cancelError: PaymentErrorCode? = null

    override fun preRegister(
        paymentId: String,
        amount: Long,
    ) {
        registered += paymentId to amount
    }

    override fun find(paymentId: String): PaymentGatewayView? = null

    override fun cancel(command: PaymentCancelCommand): PaymentCancelResult {
        cancelError?.let { throw BusinessException(it) }
        cancelCommands += command
        return PaymentCancelResult(
            status = cancelStatus,
            cancelledAmount =
                if (cancelStatus ==
                    PaymentCancelStatus.SUCCEEDED
                ) {
                    command.amount ?: command.currentCancellableAmount
                } else {
                    null
                },
            pgCancellationId = "pgcancel-1",
        )
    }
}

private class NoOpTransactionManager : AbstractPlatformTransactionManager() {
    override fun doGetTransaction(): Any = Any()

    override fun doBegin(
        transaction: Any,
        definition: TransactionDefinition,
    ) = Unit

    override fun doCommit(status: DefaultTransactionStatus) = Unit

    override fun doRollback(status: DefaultTransactionStatus) = Unit
}
