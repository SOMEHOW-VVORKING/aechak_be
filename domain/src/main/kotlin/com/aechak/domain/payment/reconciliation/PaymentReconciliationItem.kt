package com.aechak.domain.payment.reconciliation

import java.time.LocalDateTime

/**
 * 대사 건 — 결제 식별자 하나에 대한 "우리 원장 vs PG 기록" 대조 결과.
 * 양쪽 스냅샷을 그대로 남겨 두어 판정이 왜 그렇게 났는지 나중에 추적할 수 있게 한다.
 * 판정(어느 유형인지)은 외부 어휘(PG 상태)가 필요해 application이 하고, 여기는 결과와 전이만 든다.
 */
class PaymentReconciliationItem private constructor(
    val id: Long,
    val runId: Long,
    val source: ReconciliationSource,
    val paymentKey: String,
    val ours: OurLedgerSnapshot?,
    val gateway: GatewaySnapshot?,
    val mismatchType: MismatchType,
    val resolution: ReconciliationResolution,
    val resolutionNote: String?,
    val resolvedAt: LocalDateTime?,
) {
    val needsAutoResolution: Boolean get() = resolution == ReconciliationResolution.AUTO_PENDING

    /** 재대조 — 식별자는 그대로 두고 판정·스냅샷을 새것으로 바꾼다. 이미 끝난 건도 다시 판정한다(상태가 바뀌었을 수 있음) */
    fun recompare(
        ours: OurLedgerSnapshot?,
        gateway: GatewaySnapshot?,
        mismatchType: MismatchType,
        resolution: ReconciliationResolution,
    ): PaymentReconciliationItem {
        // 이미 자동 보정으로 닫힌 건이 재대조에서 일치로 나오는 건 보정이 성공했다는 뜻 — 그 사실을 지우지 않는다
        if (this.resolution == ReconciliationResolution.AUTO_CONFIRMED && mismatchType == MismatchType.MATCHED) {
            return PaymentReconciliationItem(
                id = id,
                runId = runId,
                source = source,
                paymentKey = paymentKey,
                ours = ours,
                gateway = gateway,
                mismatchType = MismatchType.MATCHED,
                resolution = this.resolution,
                resolutionNote = resolutionNote,
                resolvedAt = resolvedAt,
            )
        }
        return PaymentReconciliationItem(
            id = id,
            runId = runId,
            source = source,
            paymentKey = paymentKey,
            ours = ours,
            gateway = gateway,
            mismatchType = mismatchType,
            resolution = resolution,
            resolutionNote = null,
            resolvedAt = null,
        )
    }

    /** 보정이 끝났다. 재판정 스냅샷은 최신이지만 유형은 보정 전 것(무엇을 고쳤는지)을 남긴다 */
    fun markAutoConfirmed(
        resolvedType: MismatchType,
        now: LocalDateTime,
        note: String? = null,
    ): PaymentReconciliationItem =
        PaymentReconciliationItem(
            id = id,
            runId = runId,
            source = source,
            paymentKey = paymentKey,
            ours = ours,
            gateway = gateway,
            mismatchType = resolvedType,
            resolution = ReconciliationResolution.AUTO_CONFIRMED,
            resolutionNote = note?.take(NOTE_MAX_LENGTH),
            resolvedAt = now,
        )

    fun markFailed(
        reason: String,
        now: LocalDateTime,
    ): PaymentReconciliationItem = resolved(ReconciliationResolution.FAILED, reason, now)

    /** 사람 확인 대기로 바꾼다 — 자동 보정이 "사람 몫"으로 판명된 경우(금액 불일치·취소된 그룹) */
    fun markManualRequired(
        mismatchType: MismatchType,
        reason: String,
        now: LocalDateTime,
    ): PaymentReconciliationItem =
        PaymentReconciliationItem(
            id = id,
            runId = runId,
            source = source,
            paymentKey = paymentKey,
            ours = ours,
            gateway = gateway,
            mismatchType = mismatchType,
            resolution = ReconciliationResolution.MANUAL_REQUIRED,
            resolutionNote = reason.take(NOTE_MAX_LENGTH),
            resolvedAt = now,
        )

    /** 뒤 회차가 다시 판정했으니 이 건은 닫는다 */
    fun markSuperseded(
        byRunId: Long,
        now: LocalDateTime,
    ): PaymentReconciliationItem = resolved(ReconciliationResolution.SUPERSEDED, "run#$byRunId 에서 재판정", now)

    private fun resolved(
        resolution: ReconciliationResolution,
        note: String?,
        now: LocalDateTime,
    ): PaymentReconciliationItem =
        PaymentReconciliationItem(
            id = id,
            runId = runId,
            source = source,
            paymentKey = paymentKey,
            ours = ours,
            gateway = gateway,
            mismatchType = mismatchType,
            resolution = resolution,
            resolutionNote = note?.take(NOTE_MAX_LENGTH),
            resolvedAt = now,
        )

    companion object {
        const val NOTE_MAX_LENGTH = 500

        fun compare(
            runId: Long,
            source: ReconciliationSource,
            paymentKey: String,
            ours: OurLedgerSnapshot?,
            gateway: GatewaySnapshot?,
            mismatchType: MismatchType,
            resolution: ReconciliationResolution,
        ): PaymentReconciliationItem =
            PaymentReconciliationItem(
                id = 0L,
                runId = runId,
                source = source,
                paymentKey = paymentKey,
                ours = ours,
                gateway = gateway,
                mismatchType = mismatchType,
                resolution = resolution,
                resolutionNote = null,
                resolvedAt = null,
            )

        fun restore(
            id: Long,
            runId: Long,
            source: ReconciliationSource,
            paymentKey: String,
            ours: OurLedgerSnapshot?,
            gateway: GatewaySnapshot?,
            mismatchType: MismatchType,
            resolution: ReconciliationResolution,
            resolutionNote: String?,
            resolvedAt: LocalDateTime?,
        ): PaymentReconciliationItem =
            PaymentReconciliationItem(
                id = id,
                runId = runId,
                source = source,
                paymentKey = paymentKey,
                ours = ours,
                gateway = gateway,
                mismatchType = mismatchType,
                resolution = resolution,
                resolutionNote = resolutionNote,
                resolvedAt = resolvedAt,
            )
    }
}

/** 대조 시점의 우리 원장 — 결제 행과 주문그룹 상태. 상태 문자열은 각 enum의 name */
data class OurLedgerSnapshot(
    val paymentId: Long,
    val orderGroupId: Long,
    val paymentStatus: String,
    val groupStatus: String?,
    val groupFinalAmount: Long?,
    val targetAmount: Long,
    val paidAmount: Long?,
    val cancellableAmount: Long?,
)

/** 대조 시점의 PG 기록 — 우리 어휘로 번역된 상태와 금액 */
data class GatewaySnapshot(
    val status: String,
    val paidAmount: Long,
    val cancelledAmount: Long,
    val pgTxId: String?,
    val statusChangedAt: LocalDateTime?,
)
