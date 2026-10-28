package com.aechak.domain.payment.reconciliation

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 계약 테스트. 깨지면 재대조가 자동 보정 사실을 지우거나, 열린 건이 이월 대상에서 빠진다. */
class PaymentReconciliationItemTest {
    private val now = LocalDateTime.of(2026, 10, 16, 4, 31)

    private fun item(
        type: MismatchType,
        resolution: ReconciliationResolution,
    ) = PaymentReconciliationItem.compare(
        runId = 1L,
        source = ReconciliationSource.PORTONE,
        paymentKey = "01K2ZQZQZQZQZQZQZQZQZQZQZQ",
        ours = null,
        gateway = null,
        mismatchType = type,
        resolution = resolution,
    )

    @Test
    fun `자동 보정 대상만 보정 대기다`() {
        assertTrue(item(MismatchType.PG_PAID_OURS_UNCONFIRMED, ReconciliationResolution.AUTO_PENDING).needsAutoResolution)
        assertFalse(item(MismatchType.MATCHED, ReconciliationResolution.NOT_REQUIRED).needsAutoResolution)
    }

    @Test
    fun `자동 보정으로 닫힌 건이 재대조에서 일치로 나오면 보정 사실을 지우지 않는다`() {
        val confirmed =
            item(MismatchType.PG_PAID_OURS_UNCONFIRMED, ReconciliationResolution.AUTO_PENDING)
                .markAutoConfirmed(MismatchType.PG_PAID_OURS_UNCONFIRMED, now)

        val recompared = confirmed.recompare(null, null, MismatchType.MATCHED, ReconciliationResolution.NOT_REQUIRED)

        assertEquals(MismatchType.MATCHED, recompared.mismatchType, "지금은 일치라는 사실은 반영한다")
        assertEquals(ReconciliationResolution.AUTO_CONFIRMED, recompared.resolution, "보정이 있었다는 이력은 남아야 한다")
        assertEquals(now, recompared.resolvedAt)
    }

    @Test
    fun `재대조에서 결론이 달라지면 판정과 처리 상태를 새것으로 바꾼다`() {
        val manual = item(MismatchType.PG_ONLY_UNKNOWN, ReconciliationResolution.MANUAL_REQUIRED)

        val recompared = manual.recompare(null, null, MismatchType.MATCHED, ReconciliationResolution.NOT_REQUIRED)

        assertEquals(ReconciliationResolution.NOT_REQUIRED, recompared.resolution)
        assertNull(recompared.resolvedAt, "새 판정은 아직 아무 처리도 하지 않은 상태로 시작한다")
    }

    @Test
    fun `사람 확인·실패·보정 대기가 열린 건이고 이월 대상이다`() {
        assertTrue(ReconciliationResolution.MANUAL_REQUIRED.isOpen)
        assertTrue(ReconciliationResolution.FAILED.isOpen)
        assertTrue(ReconciliationResolution.AUTO_PENDING.isOpen, "보정 전에 회차가 죽으면 이월만이 그 건을 되살린다")
        assertFalse(ReconciliationResolution.AUTO_CONFIRMED.isOpen)
        assertFalse(ReconciliationResolution.NOT_REQUIRED.isOpen)
        assertFalse(ReconciliationResolution.SUPERSEDED.isOpen, "닫힌 건을 또 이월하면 같은 결제가 회차마다 늘어난다")
    }

    @Test
    fun `대체되면 어느 회차가 대신했는지 남는다`() {
        val superseded =
            item(
                MismatchType.PG_PAID_OURS_UNCONFIRMED,
                ReconciliationResolution.FAILED,
            ).markSuperseded(byRunId = 9L, now = now)

        assertEquals(ReconciliationResolution.SUPERSEDED, superseded.resolution)
        assertEquals("run#9 에서 재판정", superseded.resolutionNote)
    }

    @Test
    fun `사람 확인으로 바꿀 때 유형을 함께 바꾸고 사유는 길이에 맞춰 자른다`() {
        val manual =
            item(MismatchType.PG_PAID_OURS_UNCONFIRMED, ReconciliationResolution.AUTO_PENDING)
                .markManualRequired(MismatchType.AMOUNT_MISMATCH, "y".repeat(2_000), now)

        assertEquals(MismatchType.AMOUNT_MISMATCH, manual.mismatchType)
        assertEquals(ReconciliationResolution.MANUAL_REQUIRED, manual.resolution)
        assertEquals(PaymentReconciliationItem.NOTE_MAX_LENGTH, manual.resolutionNote?.length)
    }
}
