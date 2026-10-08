package com.aechak.api.support

import com.aechak.application.payment.port.PaymentCancelStatus
import com.aechak.application.payment.port.PaymentGatewayPort
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.application.payment.port.PaymentGatewayView
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 실 포트원 대신 기록만 하는 Fake.
 * 컨텍스트 수명 내내 사는 싱글턴이라 테스트 사이에 상태가 안 새게 clear로 비움.
 */
class FakePaymentGateway : PaymentGatewayPort {
    private val registered = ConcurrentHashMap<String, Long>()
    private val stubbed = ConcurrentHashMap<String, PaymentGatewayView>()
    private val cancelled = CopyOnWriteArrayList<String>()

    val cancelledPaymentIds: List<String> get() = cancelled.toList()

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
        cancelled.clear()
    }

    override fun preRegister(
        paymentId: String,
        amount: Long,
    ) {
        registered[paymentId] = amount
    }

    override fun find(paymentId: String): PaymentGatewayView? {
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
