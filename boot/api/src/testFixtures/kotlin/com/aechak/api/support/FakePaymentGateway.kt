package com.aechak.api.support

import com.aechak.application.payment.port.PaymentCancelCommand
import com.aechak.application.payment.port.PaymentCancelResult
import com.aechak.application.payment.port.PaymentCancelStatus
import com.aechak.application.payment.port.PaymentGatewayPort
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.application.payment.port.PaymentGatewayView
import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.error.PaymentErrorCode
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 실 포트원 대신 기록만 하는 Fake.
 * 컨텍스트 수명 내내 사는 싱글턴이라 테스트 사이에 상태가 안 새게 clear로 비움.
 */
class FakePaymentGateway : PaymentGatewayPort {
    private val registered = ConcurrentHashMap<String, Long>()
    private val stubbed = ConcurrentHashMap<String, PaymentGatewayView>()
    private val preRegisterCalls = CopyOnWriteArrayList<String>()
    private val cancelled = CopyOnWriteArrayList<PaymentCancelCommand>()
    private val failOnce = AtomicBoolean(false)
    private val cancelStatuses = ConcurrentHashMap<String, PaymentCancelStatus>()
    private val cancelErrors = ConcurrentHashMap<String, PaymentErrorCode>()

    @Volatile
    private var onCancel: ((PaymentCancelCommand) -> Unit)? = null

    val cancelledPaymentIds: List<String> get() = cancelled.map { it.paymentId }

    /** 취소 호출 순서대로 쌓임 — 보낸 금액과 취소가능액 대조값을 그대로 확인할 수 있다 */
    val cancelCommands: List<PaymentCancelCommand> get() = cancelled.toList()

    /** preRegister 호출 순서대로 쌓임. 실패 주입으로 끊긴 호출도 남김 */
    val preRegisteredPaymentIds: List<String> get() = preRegisterCalls.toList()

    fun registeredAmount(paymentId: String): Long? = registered[paymentId]

    fun failNextPreRegister() {
        failOnce.set(true)
    }

    /** find가 기본값(PAID) 대신 돌려줄 뷰를 심음. */
    fun stub(
        paymentId: String,
        view: PaymentGatewayView,
    ) {
        stubbed[paymentId] = view
    }

    /** cancel이 SUCCEEDED 대신 돌려줄 상태를 심음(비동기 승인 REQUESTED 등). */
    fun stubCancelStatus(
        paymentId: String,
        status: PaymentCancelStatus,
    ) {
        cancelStatuses[paymentId] = status
    }

    /** cancel 호출 안에서 돌 훅 — 취소 선점이 커밋된 뒤의 경합을 결정적으로 만드는 데 쓴다. */
    fun onCancel(hook: (PaymentCancelCommand) -> Unit) {
        onCancel = hook
    }

    /** cancel이 PG 오류로 끊기는 경로를 심음. */
    fun stubCancelError(
        paymentId: String,
        errorCode: PaymentErrorCode,
    ) {
        cancelErrors[paymentId] = errorCode
    }

    fun clear() {
        registered.clear()
        stubbed.clear()
        preRegisterCalls.clear()
        cancelled.clear()
        cancelStatuses.clear()
        cancelErrors.clear()
        onCancel = null
        failOnce.set(false)
    }

    override fun preRegister(
        paymentId: String,
        amount: Long,
    ) {
        preRegisterCalls += paymentId
        if (failOnce.getAndSet(false)) {
            throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
        }
        registered[paymentId] = amount
    }

    override fun find(paymentId: String): PaymentGatewayView? {
        stubbed[paymentId]?.let { return it }
        val amount = registered[paymentId] ?: return null
        return PaymentGatewayView(
            status = PaymentGatewayStatus.PAID,
            totalAmount = amount,
            paidAmount = amount,
            cancelledAmount = cancelledTotal(paymentId),
            pgTxId = "fake-tx-$paymentId",
            paidAt = PAID_AT,
        )
    }

    override fun cancel(command: PaymentCancelCommand): PaymentCancelResult {
        cancelErrors[command.paymentId]?.let { throw BusinessException(it) }
        cancelled += command
        onCancel?.invoke(command)
        val status = cancelStatuses[command.paymentId] ?: PaymentCancelStatus.SUCCEEDED
        return PaymentCancelResult(
            status = status,
            // 전액 취소는 amount 를 싣지 않으므로 우리가 보낸 취소가능액이 그대로 확정액이다
            cancelledAmount = if (status == PaymentCancelStatus.SUCCEEDED) command.amount ?: command.currentCancellableAmount else null,
            pgCancellationId = "fake-cancel-${command.paymentId}",
        )
    }

    /** SUCCEEDED로 끝난 취소의 누적 금액 — find가 돌려주는 PG 쪽 취소액이다 */
    private fun cancelledTotal(paymentId: String): Long =
        cancelled
            .filter { it.paymentId == paymentId && cancelStatuses[paymentId] != PaymentCancelStatus.REQUESTED }
            .sumOf { it.amount ?: it.currentCancellableAmount ?: 0L }

    companion object {
        /** PAID에는 paidAt이 필수라 없는 조합을 심지 않으려고 고정함. */
        private val PAID_AT = LocalDateTime.of(2026, 1, 1, 0, 0)
    }
}
