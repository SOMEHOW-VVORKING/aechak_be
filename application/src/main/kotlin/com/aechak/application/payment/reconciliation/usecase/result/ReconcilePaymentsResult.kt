package com.aechak.application.payment.reconciliation.usecase.result

import com.aechak.domain.payment.reconciliation.PaymentReconciliationRun
import com.aechak.domain.payment.reconciliation.ReconciliationCounts
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import java.time.LocalDate

data class ReconcilePaymentsResult(
    val source: ReconciliationSource,
    val baseDate: LocalDate,
    val outcome: ReconcileOutcome,
    val runId: Long?,
    val attempt: Int?,
    val counts: ReconciliationCounts,
    val detail: String?,
) {
    companion object {
        fun skipped(
            source: ReconciliationSource,
            baseDate: LocalDate,
            reason: String,
        ): ReconcilePaymentsResult =
            ReconcilePaymentsResult(source, baseDate, ReconcileOutcome.SKIPPED, null, null, ReconciliationCounts.ZERO, reason)

        fun from(
            run: PaymentReconciliationRun,
            outcome: ReconcileOutcome,
            detail: String? = null,
        ): ReconcilePaymentsResult = ReconcilePaymentsResult(run.source, run.baseDate, outcome, run.id, run.attempt, run.counts, detail)
    }
}

enum class ReconcileOutcome {
    /** 이미 완료됐거나 다른 실행이 돌고 있어 손대지 않음 — 멱등 재호출의 정상 결과 */
    SKIPPED,
    COMPLETED,
    FAILED,
}
