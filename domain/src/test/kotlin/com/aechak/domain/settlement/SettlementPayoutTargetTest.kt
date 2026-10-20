package com.aechak.domain.settlement

import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import com.aechak.domain.settlement.payout.Payout
import com.aechak.domain.settlement.settlement.Settlement
import com.aechak.domain.settlement.settlement.enums.SettlementStatus
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 지급 집행 대상 판정과 회차 종결 계약. 깨지면 집행 배치가 보류·환불 차감 건까지 송금하거나,
 * 입금 반영이 끝난 회차에 새 송금 요청이 섞인다.
 */
class SettlementPayoutTargetTest {
    @Test
    fun `정산대기이고 차감 없고 미연결인 원장만 지급 대상이다`() {
        assertTrue(settlement().isPayoutEligible())
    }

    @Test
    fun `보류된 원장은 지급 대상이 아니다`() {
        assertFalse(settlement().apply { hold() }.isPayoutEligible())
    }

    @Test
    fun `환불 차감이 생긴 원장은 지급 대상이 아니다`() {
        assertFalse(settlement().apply { applyRefundDeduction(1L) }.isPayoutEligible())
    }

    @Test
    fun `이미 지급된 원장은 지급 대상이 아니다`() {
        val settlement = settlement().apply { markPaid(payout()) }

        assertEquals(SettlementStatus.PAID, settlement.status)
        assertFalse(settlement.isPayoutEligible())
    }

    @Test
    fun `종결된 회차는 지급 건을 더 받지 않는다`() {
        val batch = SettlementExecutionBatch.generate(LocalDate.of(2026, 3, 20), 0, 0L)
        assertTrue(batch.acceptsPayout())

        batch.startProcessing()
        assertTrue(batch.acceptsPayout(), "입금 확인 전인 회차에는 이어서 지급 건을 붙일 수 있어야 한다")

        batch.complete()
        assertFalse(batch.acceptsPayout())
    }

    @Test
    fun `송금키는 배치와 셀러 조합으로 결정된다`() {
        assertEquals("PAYOUT:12:7001", Payout.transferKeyOf(12L, 7001L))
    }

    private fun settlement(): Settlement =
        Settlement.create(
            sellerId = SELLER_ID,
            orderId = 2L,
            orderItemId = 3L,
            grossAmount = 100_000L,
            shippingFee = 3_000L,
            commission = 8_000L,
            pgFee = 2_800L,
            sellerCouponBurden = 0L,
            platformFeeRate = BigDecimal("0.0800"),
            pgFeeRate = BigDecimal("0.0280"),
            confirmedOn = LocalDate.of(2026, 3, 10),
            idempotencyKey = Settlement.idempotencyKeyOf(3L),
        )

    private fun payout(): Payout {
        val batch = SettlementExecutionBatch.generate(LocalDate.of(2026, 3, 15), 1, 92_200L)
        return Payout.create(batch, SELLER_ID, 92_200L, Payout.transferKeyOf(batch.id, SELLER_ID))
    }

    private companion object {
        private const val SELLER_ID = 7001L
    }
}
