package com.aechak.api.support

import com.aechak.application.payment.port.GatewayPaymentRecord
import com.aechak.application.payment.port.PaymentReconciliationSourcePort
import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.error.PaymentErrorCode
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 실 PG 대신 심어 둔 기록을 돌려주는 대사 소스 Fake. 목록 조회는 심은 기록 중 창 안의 것만, 단건은 식별자로.
 * 컨텍스트 수명 내내 사는 싱글턴이라 테스트 사이에 clear로 비운다.
 */
class FakePaymentReconciliationSource(
    override val source: ReconciliationSource = ReconciliationSource.PORTONE,
    override val coversInternalLedger: Boolean = true,
    override val supportsAutoConfirmation: Boolean = true,
) : PaymentReconciliationSourcePort {
    private val records = ConcurrentHashMap<String, GatewayPaymentRecord>()
    private val singles = ConcurrentHashMap<String, GatewayPaymentRecord>()
    private val listCalls = CopyOnWriteArrayList<Pair<LocalDateTime, LocalDateTime>>()
    private val findCalls = CopyOnWriteArrayList<String>()
    private val failList = AtomicBoolean(false)

    val fetchWindows: List<Pair<LocalDateTime, LocalDateTime>> get() = listCalls.toList()
    val findCalledWith: List<String> get() = findCalls.toList()

    /** 목록·단건 양쪽에 심는다 */
    fun stub(record: GatewayPaymentRecord) {
        records[record.paymentKey] = record
        singles[record.paymentKey] = record
    }

    /** 단건 조회에만 심는다 — 목록이 낡은 상황을 흉내 낼 때 */
    fun stubSingle(record: GatewayPaymentRecord) {
        singles[record.paymentKey] = record
    }

    fun remove(paymentKey: String) {
        records.remove(paymentKey)
        singles.remove(paymentKey)
    }

    fun failNextList() {
        failList.set(true)
    }

    fun clear() {
        records.clear()
        singles.clear()
        listCalls.clear()
        findCalls.clear()
        failList.set(false)
    }

    override fun fetchChangedPayments(
        from: LocalDateTime,
        until: LocalDateTime,
    ): List<GatewayPaymentRecord> {
        listCalls += from to until
        if (failList.getAndSet(false)) throw BusinessException(PaymentErrorCode.RECONCILIATION_SOURCE_ERROR)
        return records.values
            .filter { r -> r.statusChangedAt?.let { !it.isBefore(from) && it.isBefore(until) } ?: false }
            .sortedBy { it.statusChangedAt }
    }

    override fun findByPaymentKey(paymentKey: String): GatewayPaymentRecord? {
        findCalls += paymentKey
        return singles[paymentKey]
    }
}
