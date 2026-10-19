package com.aechak.domain.settlement

import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import com.aechak.domain.settlement.error.SettlementErrorCode
import com.aechak.domain.settlement.payout.Payout
import com.aechak.domain.settlement.settlement.Settlement
import com.aechak.domain.settlement.settlement.enums.SettlementStatus
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 지급 집행 대상 판정과 지급 묶음 연결 계약. 깨지면 집행 배치가 보류·환불 차감 건까지 송금하거나,
 * 이미 묶인 정산을 다음 회차가 다시 집어 같은 돈을 두 번 내보낸다.
 */
class SettlementPayoutTargetTest {
    @Test
    fun `정산대기이고 차감 없고 미연결인 원장만 지급 대상이다`() {
        assertTrue(settlement().isPayoutEligible())
    }

    @Test
    fun `보류된 원장은 지급 대상이 아니다`() {
        val settlement = settlement().apply { hold() }

        assertFalse(settlement.isPayoutEligible())
        val exception = assertFailsWith<BusinessException> { settlement.assignPayout(payout()) }
        assertEquals(SettlementErrorCode.SETTLEMENT_NOT_PENDING, exception.errorCode)
        assertNull(settlement.payout)
    }

    @Test
    fun `환불 차감이 생긴 원장은 지급 대상이 아니다`() {
        val settlement = settlement().apply { applyRefundDeduction(1L) }

        assertFalse(settlement.isPayoutEligible())
        assertFailsWith<BusinessException> { settlement.assignPayout(payout()) }
        assertNull(settlement.payout)
    }

    @Test
    fun `지급 묶음에 넣어도 입금 확인 전이라 정산대기 그대로다`() {
        val settlement = settlement()
        val payout = payout()

        settlement.assignPayout(payout)

        assertSame(payout, settlement.payout)
        assertEquals(SettlementStatus.PENDING, settlement.status)
        // 다음 회차가 같은 원장을 다시 집지 못한다
        assertFalse(settlement.isPayoutEligible())
    }

    @Test
    fun `이미 지급 묶음에 들어간 원장은 다른 묶음에 다시 넣을 수 없다`() {
        val first = payout()
        val settlement = settlement().apply { assignPayout(first) }

        val exception = assertFailsWith<BusinessException> { settlement.assignPayout(payout()) }

        assertEquals(SettlementErrorCode.SETTLEMENT_NOT_PENDING, exception.errorCode)
        assertSame(first, settlement.payout)
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
