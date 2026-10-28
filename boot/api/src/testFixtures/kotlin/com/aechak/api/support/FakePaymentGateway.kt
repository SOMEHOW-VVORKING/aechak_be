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
 *
 * 취소는 기록만 하지 않고 PG 쪽 잔액까지 들고 있다 — 취소가능액 대조(포트원의 유일한 중복 취소 방어)와
 * 취소 뒤 조회 상태가 재현되지 않으면 "응답만 유실되고 PG는 취소에 성공한" 경로를 검증할 수 없다.
 */
class FakePaymentGateway : PaymentGatewayPort {
    private val registered = ConcurrentHashMap<String, Long>()
    private val stubbed = ConcurrentHashMap<String, PaymentGatewayView>()
    private val preRegisterCalls = CopyOnWriteArrayList<String>()
    private val cancelled = CopyOnWriteArrayList<PaymentCancelCommand>()
    private val failOnce = AtomicBoolean(false)
    private val cancelStatuses = ConcurrentHashMap<String, PaymentCancelStatus>()
    private val cancelErrors = ConcurrentHashMap<String, PaymentErrorCode>()
    private val errorsAfterProcessing = ConcurrentHashMap<String, PaymentErrorCode>()
    private val amountlessSuccess = ConcurrentHashMap<String, Boolean>()
    private val partialConfirm = ConcurrentHashMap<String, Long>()

    /** PG 쪽 확정 취소액 누적 — 실제로 처리된 취소만 더한다 */
    private val confirmedCancelled = ConcurrentHashMap<String, Long>()

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

    /** cancel이 PG에 닿기 전에 끊기는 경로를 심음 — PG는 아무것도 하지 않았다. */
    fun stubCancelError(
        paymentId: String,
        errorCode: PaymentErrorCode,
    ) {
        cancelErrors[paymentId] = errorCode
    }

    /** PG는 취소를 확정했는데 응답만 유실된 경로를 심음 — 재요청이 잔액 대조에 걸려야 한다. */
    fun stubCancelErrorAfterProcessing(
        paymentId: String,
        errorCode: PaymentErrorCode,
    ) {
        errorsAfterProcessing[paymentId] = errorCode
    }

    /** PG가 취소는 확정했지만 금액을 안 돌려주는 응답을 심음. */
    fun stubCancelWithoutAmount(paymentId: String) {
        amountlessSuccess[paymentId] = true
    }

    /** PG가 요청액보다 적은 금액만 확정해 돌려주는 응답을 심음 — 잔액도 그 금액만큼만 줄어든다. */
    fun stubCancelPartialConfirm(
        paymentId: String,
        confirmedAmount: Long,
    ) {
        partialConfirm[paymentId] = confirmedAmount
    }

    /** PG 쪽 누적 확정 취소액 */
    fun cancelledAmountOf(paymentId: String): Long = confirmedCancelled[paymentId] ?: 0L

    fun clear() {
        registered.clear()
        stubbed.clear()
        preRegisterCalls.clear()
        cancelled.clear()
        cancelStatuses.clear()
        cancelErrors.clear()
        errorsAfterProcessing.clear()
        amountlessSuccess.clear()
        partialConfirm.clear()
        confirmedCancelled.clear()
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
        val cancelled = cancelledAmountOf(paymentId)
        return PaymentGatewayView(
            status =
                when {
                    cancelled == 0L -> PaymentGatewayStatus.PAID
                    cancelled < amount -> PaymentGatewayStatus.PARTIAL_CANCELLED
                    else -> PaymentGatewayStatus.CANCELLED
                },
            totalAmount = amount,
            paidAmount = amount,
            cancelledAmount = cancelled,
            pgTxId = "fake-tx-$paymentId",
            paidAt = PAID_AT,
        )
    }

    override fun cancel(command: PaymentCancelCommand): PaymentCancelResult {
        cancelErrors[command.paymentId]?.let { throw BusinessException(it) }
        cancelled += command
        onCancel?.invoke(command)
        assertCancellableAgrees(command)
        val status = cancelStatuses[command.paymentId] ?: PaymentCancelStatus.SUCCEEDED
        // 전액 취소는 amount 를 싣지 않으므로 우리가 보낸 취소가능액이 그대로 확정액이다.
        // 부분 확정 주입이 있으면 PG가 요청액보다 적게 취소한 경우를 재현한다
        val amount = partialConfirm[command.paymentId] ?: command.amount ?: command.currentCancellableAmount
        if (status == PaymentCancelStatus.SUCCEEDED) {
            confirmedCancelled.merge(command.paymentId, amount, Long::plus)
        }
        // 확정 뒤에 끊는 주입 — 호출자는 결과를 모르지만 PG는 이미 취소했다
        errorsAfterProcessing[command.paymentId]?.let { throw BusinessException(it) }
        val reportAmount = status == PaymentCancelStatus.SUCCEEDED && amountlessSuccess[command.paymentId] != true
        return PaymentCancelResult(
            status = status,
            cancelledAmount = if (reportAmount) amount else null,
            pgCancellationId = "fake-cancel-${command.paymentId}",
        )
    }

    /** 포트원의 중복 취소 방어를 재현한다 — 보낸 취소가능액이 PG 잔액과 다르면 거절한다 */
    private fun assertCancellableAgrees(command: PaymentCancelCommand) {
        val paid = registered[command.paymentId] ?: throw BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND)
        val remaining = paid - cancelledAmountOf(command.paymentId)
        if (command.currentCancellableAmount != remaining) {
            throw BusinessException(PaymentErrorCode.PAYMENT_CANCELLABLE_AMOUNT_CONFLICT)
        }
        if ((command.amount ?: remaining) > remaining) {
            throw BusinessException(PaymentErrorCode.CANCEL_AMOUNT_EXCEEDS_CANCELLABLE)
        }
    }

    companion object {
        /** PAID에는 paidAt이 필수라 없는 조합을 심지 않으려고 고정함. */
        private val PAID_AT = LocalDateTime.of(2026, 1, 1, 0, 0)
    }
}
