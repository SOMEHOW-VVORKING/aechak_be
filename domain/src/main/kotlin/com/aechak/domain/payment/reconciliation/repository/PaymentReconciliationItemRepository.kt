package com.aechak.domain.payment.reconciliation.repository

import com.aechak.domain.payment.reconciliation.PaymentReconciliationItem
import com.aechak.domain.payment.reconciliation.ReconciliationResolution
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import java.time.LocalDate
import java.time.LocalDateTime

interface PaymentReconciliationItemRepository {
    fun save(item: PaymentReconciliationItem): PaymentReconciliationItem

    /** 회차 안에서 결제 식별자는 UNIQUE — 재대조는 이 행을 덮어쓴다 */
    fun findByRunIdAndPaymentKey(
        runId: Long,
        paymentKey: String,
    ): PaymentReconciliationItem?

    fun findAllByRunIdAndResolution(
        runId: Long,
        resolution: ReconciliationResolution,
    ): List<PaymentReconciliationItem>

    fun findAllByRunId(runId: Long): List<PaymentReconciliationItem>

    /**
     * 이월 대상 — 기준일이 더 이른 회차 가운데 끝났거나(COMPLETED·FAILED) 죽은(RUNNING인데 staleBefore보다 먼저 시작) 회차에 남은 열린 건.
     * 살아서 도는 회차의 건은 제외한다 — 서로 다른 날짜를 동시에 돌리는 두 실행이 상대의 건을 닫지 않게.
     */
    fun findOpenItemsForCarryOver(
        source: ReconciliationSource,
        beforeBaseDate: LocalDate,
        staleBefore: LocalDateTime,
    ): List<PaymentReconciliationItem>

    fun findById(id: Long): PaymentReconciliationItem?
}
