package com.aechak.infra.persistence.payment.reconciliation

import com.aechak.domain.payment.reconciliation.GatewaySnapshot
import com.aechak.domain.payment.reconciliation.OurLedgerSnapshot
import com.aechak.domain.payment.reconciliation.PaymentReconciliationItem
import com.aechak.domain.payment.reconciliation.PaymentReconciliationRun
import com.aechak.domain.payment.reconciliation.ReconciliationCounts

/** 미매핑 컬럼은 merge 저장에서 덮이므로 전량 왕복시킴(PaymentMapper와 같은 규약) */
internal object PaymentReconciliationMapper {
    fun toEntity(run: PaymentReconciliationRun): PaymentReconciliationRunJpaEntity =
        PaymentReconciliationRunJpaEntity(
            id = run.id,
            source = run.source,
            baseDate = run.baseDate,
            windowFrom = run.windowFrom,
            windowUntil = run.windowUntil,
            status = run.status,
            attempt = run.attempt,
            fetchedCount = run.counts.fetched,
            comparedCount = run.counts.compared,
            matchedCount = run.counts.matched,
            autoResolvedCount = run.counts.autoResolved,
            manualRequiredCount = run.counts.manualRequired,
            failedCount = run.counts.failed,
            startedAt = run.startedAt,
            finishedAt = run.finishedAt,
            errorMessage = run.errorMessage,
            version = run.version,
        )

    fun toDomain(entity: PaymentReconciliationRunJpaEntity): PaymentReconciliationRun =
        PaymentReconciliationRun.restore(
            id = entity.id,
            source = entity.source,
            baseDate = entity.baseDate,
            windowFrom = entity.windowFrom,
            windowUntil = entity.windowUntil,
            status = entity.status,
            attempt = entity.attempt,
            counts =
                ReconciliationCounts(
                    fetched = entity.fetchedCount,
                    compared = entity.comparedCount,
                    matched = entity.matchedCount,
                    autoResolved = entity.autoResolvedCount,
                    manualRequired = entity.manualRequiredCount,
                    failed = entity.failedCount,
                ),
            startedAt = entity.startedAt,
            finishedAt = entity.finishedAt,
            errorMessage = entity.errorMessage,
            version = entity.version,
        )

    fun toEntity(item: PaymentReconciliationItem): PaymentReconciliationItemJpaEntity =
        PaymentReconciliationItemJpaEntity(
            id = item.id,
            runId = item.runId,
            source = item.source,
            paymentKey = item.paymentKey,
            paymentId = item.ours?.paymentId,
            orderGroupId = item.ours?.orderGroupId,
            ourPaymentStatus = item.ours?.paymentStatus,
            ourGroupStatus = item.ours?.groupStatus,
            ourGroupFinalAmount = item.ours?.groupFinalAmount,
            ourTargetAmount = item.ours?.targetAmount,
            ourPaidAmount = item.ours?.paidAmount,
            ourCancellableAmount = item.ours?.cancellableAmount,
            pgStatus = item.gateway?.status,
            pgPaidAmount = item.gateway?.paidAmount,
            pgCancelledAmount = item.gateway?.cancelledAmount,
            pgTxId = item.gateway?.pgTxId,
            pgStatusChangedAt = item.gateway?.statusChangedAt,
            mismatchType = item.mismatchType,
            resolution = item.resolution,
            resolutionNote = item.resolutionNote,
            resolvedAt = item.resolvedAt,
        )

    fun toDomain(entity: PaymentReconciliationItemJpaEntity): PaymentReconciliationItem =
        PaymentReconciliationItem.restore(
            id = entity.id,
            runId = entity.runId,
            source = entity.source,
            paymentKey = entity.paymentKey,
            ours =
                entity.paymentId?.let { paymentId ->
                    OurLedgerSnapshot(
                        paymentId = paymentId,
                        orderGroupId = requireNotNull(entity.orderGroupId) { "결제 행이 있으면 주문그룹도 있어야 합니다 (itemId=${entity.id})" },
                        paymentStatus = requireNotNull(entity.ourPaymentStatus) { "결제 행이 있으면 상태도 있어야 합니다 (itemId=${entity.id})" },
                        groupStatus = entity.ourGroupStatus,
                        groupFinalAmount = entity.ourGroupFinalAmount,
                        targetAmount = requireNotNull(entity.ourTargetAmount) { "결제 행이 있으면 등록금액도 있어야 합니다 (itemId=${entity.id})" },
                        paidAmount = entity.ourPaidAmount,
                        cancellableAmount = entity.ourCancellableAmount,
                    )
                },
            gateway =
                entity.pgStatus?.let { status ->
                    GatewaySnapshot(
                        status = status,
                        paidAmount = entity.pgPaidAmount ?: 0L,
                        cancelledAmount = entity.pgCancelledAmount ?: 0L,
                        pgTxId = entity.pgTxId,
                        statusChangedAt = entity.pgStatusChangedAt,
                    )
                },
            mismatchType = entity.mismatchType,
            resolution = entity.resolution,
            resolutionNote = entity.resolutionNote,
            resolvedAt = entity.resolvedAt,
        )
}
