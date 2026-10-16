package com.aechak.infra.persistence.payment.reconciliation

import com.aechak.domain.payment.reconciliation.GatewaySnapshot
import com.aechak.domain.payment.reconciliation.MismatchType
import com.aechak.domain.payment.reconciliation.OurLedgerSnapshot
import com.aechak.domain.payment.reconciliation.PaymentReconciliationItem
import com.aechak.domain.payment.reconciliation.PaymentReconciliationRun
import com.aechak.domain.payment.reconciliation.ReconciliationCounts
import com.aechak.domain.payment.reconciliation.ReconciliationResolution
import com.aechak.domain.payment.reconciliation.ReconciliationRunStatus
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 계약 테스트. 저장이 merge라 매핑에서 빠진 컬럼은 NULL로 덮인다 — 회차의 version·건수·시각, 건의 양쪽 스냅샷을 값 하나씩 왕복한다.
 */
class PaymentReconciliationMapperTest {
    @Test
    fun `완료된 회차는 건수와 버전과 시각을 전부 왕복한다`() {
        val run =
            PaymentReconciliationRun.restore(
                id = 11L,
                source = ReconciliationSource.PORTONE,
                baseDate = LocalDate.of(2026, 10, 15),
                windowFrom = LocalDateTime.of(2026, 10, 14, 0, 0),
                windowUntil = LocalDateTime.of(2026, 10, 16, 0, 0),
                status = ReconciliationRunStatus.COMPLETED,
                attempt = 2,
                counts = ReconciliationCounts(fetched = 10, compared = 12, matched = 9, autoResolved = 1, manualRequired = 1, failed = 1),
                startedAt = LocalDateTime.of(2026, 10, 16, 4, 30),
                finishedAt = LocalDateTime.of(2026, 10, 16, 4, 31, 7),
                errorMessage = null,
                version = 5,
            )

        val back = PaymentReconciliationMapper.toDomain(PaymentReconciliationMapper.toEntity(run))

        assertEquals(run.id, back.id)
        assertEquals(run.source, back.source)
        assertEquals(run.baseDate, back.baseDate)
        assertEquals(run.windowFrom, back.windowFrom)
        assertEquals(run.windowUntil, back.windowUntil)
        assertEquals(run.status, back.status)
        assertEquals(run.attempt, back.attempt)
        assertEquals(run.counts, back.counts)
        assertEquals(run.startedAt, back.startedAt)
        assertEquals(run.finishedAt, back.finishedAt)
        assertNull(back.errorMessage)
        assertEquals(run.version, back.version, "version이 빠지면 낙관적 락이 무력화돼 이어받기 경쟁을 못 가린다")
    }

    @Test
    fun `양쪽 스냅샷이 있는 건은 전 컬럼을 왕복한다`() {
        val item =
            PaymentReconciliationItem.restore(
                id = 21L,
                runId = 11L,
                source = ReconciliationSource.TOSS,
                paymentKey = "01K2ZQZQZQZQZQZQZQZQZQZQZQ",
                ours =
                    OurLedgerSnapshot(
                        paymentId = 7L,
                        orderGroupId = 3L,
                        paymentStatus = "APPROVED",
                        groupStatus = "PAID",
                        groupFinalAmount = 13_000L,
                        targetAmount = 13_000L,
                        paidAmount = 13_000L,
                        cancellableAmount = 13_000L,
                    ),
                gateway =
                    GatewaySnapshot(
                        status = "PAID",
                        paidAmount = 13_000L,
                        cancelledAmount = 0L,
                        pgTxId = "pg-tx-1",
                        statusChangedAt = LocalDateTime.of(2026, 10, 15, 12, 0),
                    ),
                mismatchType = MismatchType.MATCHED,
                resolution = ReconciliationResolution.AUTO_CONFIRMED,
                resolutionNote = "보정",
                resolvedAt = LocalDateTime.of(2026, 10, 16, 4, 30, 30),
            )

        val back = PaymentReconciliationMapper.toDomain(PaymentReconciliationMapper.toEntity(item))

        assertEquals(item.id, back.id)
        assertEquals(item.runId, back.runId)
        assertEquals(item.source, back.source)
        assertEquals(item.paymentKey, back.paymentKey)
        assertEquals(item.ours, back.ours)
        assertEquals(item.gateway, back.gateway)
        assertEquals(item.mismatchType, back.mismatchType)
        assertEquals(item.resolution, back.resolution)
        assertEquals(item.resolutionNote, back.resolutionNote)
        assertEquals(item.resolvedAt, back.resolvedAt)
    }

    @Test
    fun `PG에만 있는 건과 우리에만 있는 건은 빈 쪽을 null로 왕복한다`() {
        val pgOnly =
            PaymentReconciliationItem.compare(
                runId = 11L,
                source = ReconciliationSource.PORTONE,
                paymentKey = "unknown-key",
                ours = null,
                gateway = GatewaySnapshot("PAID", 5_000L, 0L, null, null),
                mismatchType = MismatchType.PG_ONLY_UNKNOWN,
                resolution = ReconciliationResolution.MANUAL_REQUIRED,
            )
        val oursOnly =
            PaymentReconciliationItem.compare(
                runId = 11L,
                source = ReconciliationSource.PORTONE,
                paymentKey = "ours-key",
                ours = OurLedgerSnapshot(8L, 4L, "APPROVED", "PAID", 9_000L, 9_000L, 9_000L, 9_000L),
                gateway = null,
                mismatchType = MismatchType.PG_NOT_PAID_OURS_APPROVED,
                resolution = ReconciliationResolution.MANUAL_REQUIRED,
            )

        val pgOnlyBack = PaymentReconciliationMapper.toDomain(PaymentReconciliationMapper.toEntity(pgOnly))
        val oursOnlyBack = PaymentReconciliationMapper.toDomain(PaymentReconciliationMapper.toEntity(oursOnly))

        assertNull(pgOnlyBack.ours)
        assertEquals(pgOnly.gateway, pgOnlyBack.gateway)
        assertNull(oursOnlyBack.gateway)
        assertEquals(oursOnly.ours, oursOnlyBack.ours)
    }
}
