package com.aechak.domain.settlement

import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import com.aechak.domain.settlement.error.SettlementErrorCode
import com.aechak.domain.settlement.payout.Payout
import com.aechak.domain.settlement.payout.enums.PayoutStatus
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 지급 상태머신 계약. 깨지면 송금하지 않은 지급이 입금 완료로 보이거나,
 * 실패한 송금을 다시 보낼 경로가 사라진다.
 */
class PayoutTest {
    @Test
    fun `송금 요청은 지급사 식별자와 처리 시각을 남긴다`() {
        val payout = payout()

        payout.markSent("portone-1")

        assertEquals(PayoutStatus.SENT, payout.status)
        assertEquals("portone-1", payout.portonePayoutId)
        assertNotNull(payout.processedAt)
    }

    @Test
    fun `송금 요청 없이 입금 완료로 갈 수 없다`() {
        val payout = payout()

        val exception = assertFailsWith<BusinessException> { payout.markPaid(LocalDateTime.now()) }

        assertEquals(SettlementErrorCode.INVALID_PAYOUT_STATUS_TRANSITION, exception.errorCode)
        assertEquals(PayoutStatus.PENDING, payout.status)
        assertNull(payout.depositedAt)
    }

    @Test
    fun `송금 요청된 지급은 입금 완료로 간다`() {
        val depositedAt = LocalDateTime.of(2026, 3, 20, 10, 0)
        val payout = payout().apply { markSent("portone-1") }

        payout.markPaid(depositedAt)

        assertEquals(PayoutStatus.PAID, payout.status)
        assertEquals(depositedAt, payout.depositedAt)
    }

    @Test
    fun `실패한 지급은 사유를 남기고 같은 송금키로 다시 보낼 수 있다`() {
        val payout = payout().apply { markFailed("계좌 정보 불일치") }

        assertEquals(PayoutStatus.FAILED, payout.status)
        assertEquals("계좌 정보 불일치", payout.failReason)

        payout.markSent("portone-2")

        assertEquals(PayoutStatus.SENT, payout.status)
        assertNull(payout.failReason, "재송금은 지난 실패 사유를 지운다")
    }

    @Test
    fun `입금 완료된 지급은 다시 전이되지 않는다`() {
        val payout = payout().apply { markSent("portone-1") }.apply { markPaid(LocalDateTime.now()) }

        assertFailsWith<BusinessException> { payout.markFailed("뒤늦은 실패 통보") }
        assertEquals(PayoutStatus.PAID, payout.status)
    }

    private fun payout(): Payout =
        Payout.create(
            batch = SettlementExecutionBatch.generate(LocalDate.of(2026, 3, 20), 2, 50_000L),
            sellerId = 1L,
            settlementTargetAmount = 50_000L,
            transferKey = "PAYOUT:1:1",
        )
}
