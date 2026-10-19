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

/**
 * 정산 원장 1행의 금액 공식과 상태머신 계약. 깨지면 셀러에게 지급할 금액이 틀리거나,
 * 보류 중인 정산이 지급 대상으로 새어 들어간다.
 */
class SettlementTest {
    @Test
    fun `지급 예정액은 상품금액과 배송비에서 수수료와 셀러 쿠폰 부담을 뺀 값이다`() {
        val settlement =
            settlement(grossAmount = 100_000L, shippingFee = 3_000L, commission = 8_000L, pgFee = 2_800L, sellerCouponBurden = 1_000L)

        assertEquals(91_200L, settlement.netAmount)
        assertEquals(0L, settlement.refundDeduction)
    }

    @Test
    fun `정산월과 지급 예정일은 구매확정일에서 뽑는다`() {
        val settlement = settlement(confirmedOn = LocalDate.of(2026, 1, 29))

        assertEquals("2026-01", settlement.settlementMonth)
        assertEquals(LocalDate.of(2026, 2, 3), settlement.expectedPayoutDate, "역일 5일 — 주말·공휴일을 건너뛰지 않는다")
    }

    @Test
    fun `음수 금액은 원장이 되지 못한다`() {
        val exception = assertFailsWith<BusinessException> { settlement(commission = -1L) }

        assertEquals(SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT, exception.errorCode)
    }

    @Test
    fun `진행 중 클레임이 있는 정산은 보류되고 해제되면 다시 지급 대상이 된다`() {
        val settlement = settlement()

        settlement.hold()
        assertEquals(SettlementStatus.ON_HOLD, settlement.status)

        settlement.release()
        assertEquals(SettlementStatus.PENDING, settlement.status)
    }

    @Test
    fun `보류 중인 정산은 지급 완료로 갈 수 없다`() {
        val settlement = settlement().apply { hold() }

        val exception = assertFailsWith<BusinessException> { settlement.markPaid(payout()) }

        assertEquals(SettlementErrorCode.INVALID_SETTLEMENT_STATUS_TRANSITION, exception.errorCode)
        assertEquals(SettlementStatus.ON_HOLD, settlement.status, "거절된 전이가 상태를 바꾸면 안 된다")
    }

    @Test
    fun `지급 완료된 정산은 다시 전이되지 않는다`() {
        val settlement = settlement().apply { markPaid(payout()) }

        assertFailsWith<BusinessException> { settlement.cancel() }
        assertFailsWith<BusinessException> { settlement.hold() }
        assertEquals(SettlementStatus.PAID, settlement.status)
    }

    @Test
    fun `환불 확정분은 차감액에 쌓이고 지급 예정액에서 빠진다`() {
        val settlement = settlement(grossAmount = 100_000L, shippingFee = 0L, commission = 8_000L, pgFee = 2_800L, sellerCouponBurden = 0L)

        settlement.applyRefundDeduction(30_000L)
        settlement.applyRefundDeduction(20_000L)

        assertEquals(50_000L, settlement.refundDeduction)
        assertEquals(39_200L, settlement.netAmount)
    }

    @Test
    fun `보류 중인 정산도 환불 차감을 받는다`() {
        val settlement = settlement().apply { hold() }

        settlement.applyRefundDeduction(1_000L)

        assertEquals(1_000L, settlement.refundDeduction)
    }

    @Test
    fun `받은 돈보다 큰 환불 차감은 거절된다`() {
        val settlement = settlement(grossAmount = 10_000L, shippingFee = 3_000L)

        settlement.applyRefundDeduction(13_000L)
        val exception = assertFailsWith<BusinessException> { settlement.applyRefundDeduction(1L) }

        assertEquals(SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT, exception.errorCode)
        assertEquals(13_000L, settlement.refundDeduction, "거절된 차감이 금액을 바꾸면 안 된다")
    }

    @Test
    fun `지급 완료된 정산은 환불 차감을 받지 않는다`() {
        val settlement = settlement().apply { markPaid(payout()) }

        val exception = assertFailsWith<BusinessException> { settlement.applyRefundDeduction(1_000L) }

        assertEquals(SettlementErrorCode.SETTLEMENT_NOT_PENDING, exception.errorCode)
    }

    @Test
    fun `저장할 수 없는 요율 스냅샷은 원장이 되지 못한다`() {
        val exception =
            assertFailsWith<BusinessException> {
                Settlement.create(
                    sellerId = 1L,
                    orderId = 2L,
                    orderItemId = 3L,
                    grossAmount = 100_000L,
                    shippingFee = 0L,
                    commission = 8_005L,
                    pgFee = 2_800L,
                    sellerCouponBurden = 0L,
                    platformFeeRate = BigDecimal("0.08005"),
                    pgFeeRate = BigDecimal("0.0280"),
                    confirmedOn = LocalDate.of(2026, 3, 10),
                    idempotencyKey = "SETTLE:ORDER_ITEM:3",
                )
            }

        assertEquals(SettlementErrorCode.INVALID_SETTLEMENT_FEE_RATE, exception.errorCode)
    }

    private fun settlement(
        grossAmount: Long = 100_000L,
        shippingFee: Long = 3_000L,
        commission: Long = 8_000L,
        pgFee: Long = 2_800L,
        sellerCouponBurden: Long = 0L,
        confirmedOn: LocalDate = LocalDate.of(2026, 3, 10),
    ): Settlement =
        Settlement.create(
            sellerId = 1L,
            orderId = 2L,
            orderItemId = 3L,
            grossAmount = grossAmount,
            shippingFee = shippingFee,
            commission = commission,
            pgFee = pgFee,
            sellerCouponBurden = sellerCouponBurden,
            platformFeeRate = BigDecimal("0.0800"),
            pgFeeRate = BigDecimal("0.0280"),
            confirmedOn = confirmedOn,
            idempotencyKey = "SETTLE:ORDER_ITEM:3",
        )

    private fun payout() =
        Payout.create(
            batch = SettlementExecutionBatch.generate(LocalDate.of(2026, 3, 15), 1, 1_000L),
            sellerId = 1L,
            settlementTargetAmount = 1_000L,
            transferKey = "PAYOUT:1:1",
        )
}
