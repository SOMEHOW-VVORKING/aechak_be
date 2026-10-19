package com.aechak.application.payment.facade

import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.command.AutoConfirmPurchaseCommand
import com.aechak.application.order.usecase.command.ConfirmPurchaseCommand
import com.aechak.application.order.usecase.command.CreateOrderGroupCommand
import com.aechak.application.order.usecase.command.MarkConfirmNotifiedCommand
import com.aechak.application.order.usecase.query.AutoConfirmScanQuery
import com.aechak.application.order.usecase.query.ConfirmNotifyScanQuery
import com.aechak.application.order.usecase.result.ConfirmGroupPaidResult
import com.aechak.application.order.usecase.result.CreateOrderGroupResult
import com.aechak.application.order.usecase.result.OrderItemForReviewResult
import com.aechak.application.payment.port.PaymentCancelStatus
import com.aechak.application.payment.port.PaymentGatewayPort
import com.aechak.application.payment.port.PaymentGatewayView
import com.aechak.application.payment.port.PaymentWebhookNotification
import com.aechak.application.payment.port.PaymentWebhookRequest
import com.aechak.application.payment.port.PaymentWebhookVerifier
import com.aechak.application.payment.service.PaymentService
import com.aechak.application.payment.usecase.command.PreparePaymentCommand
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.repository.OrderGroupRepository
import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.repository.PaymentRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

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
}

/** 결제 준비 흐름은 주문 전이를 부르지 않는다 — 호출되면 테스트 전제가 깨진 것 */
private class UnusedOrderUseCase : OrderUseCase {
    override fun createOrderGroup(command: CreateOrderGroupCommand): CreateOrderGroupResult = throw UnsupportedOperationException()

    override fun getOrderItemForReview(
        orderItemId: Long,
        buyerId: Long,
    ): OrderItemForReviewResult? = throw UnsupportedOperationException()

    override fun confirmGroupPaid(orderGroupId: Long): ConfirmGroupPaidResult = throw UnsupportedOperationException()

    override fun confirmPurchase(command: ConfirmPurchaseCommand) = throw UnsupportedOperationException()

    override fun findAutoConfirmTargetIds(query: AutoConfirmScanQuery): List<Long> = throw UnsupportedOperationException()

    override fun autoConfirmPurchase(command: AutoConfirmPurchaseCommand) = throw UnsupportedOperationException()

    override fun findConfirmNotifyTargetIds(query: ConfirmNotifyScanQuery): List<Long> = throw UnsupportedOperationException()

    override fun markConfirmNotified(command: MarkConfirmNotifiedCommand): Boolean = throw UnsupportedOperationException()

    override fun clearOrderedCartItems(
        buyerId: Long,
        orderGroupId: Long,
    ): Int = throw UnsupportedOperationException()
}

private class FakePaymentRepository : PaymentRepository {
    private val byOrderGroupId = mutableMapOf<Long, Payment>()
    private var competitor: Payment? = null

    /** 다음 save 한 번은 경쟁 요청이 먼저 커밋한 것처럼 winner를 남기고 UNIQUE 위반을 던짐 */
    fun loseNextSaveTo(winner: Payment) {
        competitor = winner
    }

    override fun save(payment: Payment): Payment {
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

    override fun preRegister(
        paymentId: String,
        amount: Long,
    ) {
        registered += paymentId to amount
    }

    override fun find(paymentId: String): PaymentGatewayView? = null

    override fun cancel(
        paymentId: String,
        reason: String,
    ): PaymentCancelStatus = PaymentCancelStatus.SUCCEEDED
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
