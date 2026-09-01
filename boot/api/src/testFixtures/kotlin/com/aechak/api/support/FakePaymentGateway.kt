package com.aechak.api.support

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
    private val cancelled = CopyOnWriteArrayList<String>()
    private val failOnce = AtomicBoolean(false)
    private val failingFinds = ConcurrentHashMap.newKeySet<String>()
    private val failAllFinds = AtomicBoolean(false)

    val cancelledPaymentIds: List<String> get() = cancelled.toList()

    /** 해당 결제건 조회를 게이트웨이 오류로 만듦. 만료 배치의 건별 격리 검증용 */
    fun failFindFor(paymentId: String) {
        failingFinds += paymentId
    }

    /** 모든 조회를 게이트웨이 오류로 만듦. 포트원 장애 상황 재현용 */
    fun failAllFinds() {
        failAllFinds.set(true)
    }

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

    fun clear() {
        registered.clear()
        stubbed.clear()
        preRegisterCalls.clear()
        cancelled.clear()
        failOnce.set(false)
        failingFinds.clear()
        failAllFinds.set(false)
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
        if (failAllFinds.get() || paymentId in failingFinds) {
            throw BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
        }
        stubbed[paymentId]?.let { return it }
        val amount = registered[paymentId] ?: return null
        return PaymentGatewayView(
            status = PaymentGatewayStatus.PAID,
            totalAmount = amount,
            paidAmount = amount,
            pgTxId = "fake-tx-$paymentId",
            paidAt = PAID_AT,
        )
    }

    override fun cancel(
        paymentId: String,
        reason: String,
    ): PaymentCancelStatus {
        cancelled += paymentId
        return PaymentCancelStatus.SUCCEEDED
    }

    companion object {
        /** PAID에는 paidAt이 필수라 없는 조합을 심지 않으려고 고정함. */
        private val PAID_AT = LocalDateTime.of(2026, 1, 1, 0, 0)
    }
}
