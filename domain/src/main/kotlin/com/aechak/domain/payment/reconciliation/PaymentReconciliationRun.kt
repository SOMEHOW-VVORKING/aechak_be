package com.aechak.domain.payment.reconciliation

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 대사 회차. (소스, 기준일)당 하나만 존재하며 완료된 회차는 다시 돌지 않는다.
 * 결제 BC의 L3 규칙을 따라 순수 모델이고 JPA 엔티티는 infra가 따로 든다.
 */
class PaymentReconciliationRun private constructor(
    val id: Long,
    val source: ReconciliationSource,
    val baseDate: LocalDate,
    val windowFrom: LocalDateTime,
    val windowUntil: LocalDateTime,
    val status: ReconciliationRunStatus,
    val attempt: Int,
    val counts: ReconciliationCounts,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?,
    val errorMessage: String?,
    val version: Int,
) {
    /** 진행 중인데 너무 오래 걸렸으면 인스턴스가 죽은 것으로 본다 — 그래야 다음 실행이 이어받는다 */
    fun isStale(
        now: LocalDateTime,
        staleAfter: Duration,
    ): Boolean = status == ReconciliationRunStatus.RUNNING && startedAt.plus(staleAfter).isBefore(now)

    /** 이어받아도 되는 회차 — 실패했거나 죽은 것. 완료됐거나 살아서 도는 중이면 손대지 않는다 */
    fun canBeTakenOver(
        now: LocalDateTime,
        staleAfter: Duration,
    ): Boolean = status == ReconciliationRunStatus.FAILED || isStale(now, staleAfter)

    /** 내 세대(attempt)가 아직 유효한가 — 건 기록·완료 처리 전에 run을 다시 읽어 확인한다 */
    fun isOwnedBy(attempt: Int): Boolean = this.attempt == attempt && status == ReconciliationRunStatus.RUNNING

    /** 실패했거나 죽은 회차를 이어받는다. 횟수를 올려 몇 번째 시도인지 남긴다 */
    fun resume(now: LocalDateTime): PaymentReconciliationRun =
        copy(
            status = ReconciliationRunStatus.RUNNING,
            attempt = attempt + 1,
            counts = ReconciliationCounts.ZERO,
            startedAt = now,
            finishedAt = null,
            errorMessage = null,
        )

    fun complete(
        counts: ReconciliationCounts,
        now: LocalDateTime,
    ): PaymentReconciliationRun = copy(status = ReconciliationRunStatus.COMPLETED, counts = counts, finishedAt = now, errorMessage = null)

    fun fail(
        counts: ReconciliationCounts,
        message: String,
        now: LocalDateTime,
    ): PaymentReconciliationRun =
        copy(
            status = ReconciliationRunStatus.FAILED,
            counts = counts,
            finishedAt = now,
            errorMessage = message.take(ERROR_MESSAGE_MAX_LENGTH),
        )

    private fun copy(
        status: ReconciliationRunStatus = this.status,
        attempt: Int = this.attempt,
        counts: ReconciliationCounts = this.counts,
        startedAt: LocalDateTime = this.startedAt,
        finishedAt: LocalDateTime? = this.finishedAt,
        errorMessage: String? = this.errorMessage,
    ): PaymentReconciliationRun =
        PaymentReconciliationRun(
            id = id,
            source = source,
            baseDate = baseDate,
            windowFrom = windowFrom,
            windowUntil = windowUntil,
            status = status,
            attempt = attempt,
            counts = counts,
            startedAt = startedAt,
            finishedAt = finishedAt,
            errorMessage = errorMessage,
            version = version,
        )

    companion object {
        const val ERROR_MESSAGE_MAX_LENGTH = 1000

        fun start(
            source: ReconciliationSource,
            baseDate: LocalDate,
            windowFrom: LocalDateTime,
            windowUntil: LocalDateTime,
            now: LocalDateTime,
        ): PaymentReconciliationRun {
            require(windowFrom.isBefore(windowUntil)) { "대사 창의 시작이 끝보다 앞서야 합니다: $windowFrom ~ $windowUntil" }
            return PaymentReconciliationRun(
                id = 0L,
                source = source,
                baseDate = baseDate,
                windowFrom = windowFrom,
                windowUntil = windowUntil,
                status = ReconciliationRunStatus.RUNNING,
                attempt = 1,
                counts = ReconciliationCounts.ZERO,
                startedAt = now,
                finishedAt = null,
                errorMessage = null,
                version = 0,
            )
        }

        fun restore(
            id: Long,
            source: ReconciliationSource,
            baseDate: LocalDate,
            windowFrom: LocalDateTime,
            windowUntil: LocalDateTime,
            status: ReconciliationRunStatus,
            attempt: Int,
            counts: ReconciliationCounts,
            startedAt: LocalDateTime,
            finishedAt: LocalDateTime?,
            errorMessage: String?,
            version: Int,
        ): PaymentReconciliationRun =
            PaymentReconciliationRun(
                id = id,
                source = source,
                baseDate = baseDate,
                windowFrom = windowFrom,
                windowUntil = windowUntil,
                status = status,
                attempt = attempt,
                counts = counts,
                startedAt = startedAt,
                finishedAt = finishedAt,
                errorMessage = errorMessage,
                version = version,
            )
    }
}

/** 회차 요약 건수. 전부 0부터 세고 마무리 때 한 번에 기록한다 */
data class ReconciliationCounts(
    val fetched: Int,
    val compared: Int,
    val matched: Int,
    val autoResolved: Int,
    val manualRequired: Int,
    val failed: Int,
) {
    /** 사람이 봐야 하는 것이 하나라도 있으면 true — 알람 기준 */
    val needsAttention: Boolean get() = manualRequired > 0 || failed > 0

    companion object {
        val ZERO = ReconciliationCounts(0, 0, 0, 0, 0, 0)
    }
}
