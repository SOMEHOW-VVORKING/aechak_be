package com.aechak.domain.settlement

import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.error.SettlementErrorCode
import com.aechak.domain.settlement.feepolicy.SettlementFeePolicy
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 수수료율 정책의 금액 환산 계약. 깨지면 셀러에게 떼는 수수료가 1원 단위로 틀어진다.
 */
class SettlementFeePolicyTest {
    @Test
    fun `기본 요율은 플랫폼 8퍼센트와 결제 2점8퍼센트다`() {
        val policy = globalPolicy()

        assertEquals(8_000L, policy.platformFeeOf(100_000L))
        assertEquals(2_800L, policy.pgFeeOf(100_000L))
        assertTrue(policy.isGlobal())
    }

    @Test
    fun `원 단위 아래는 버린다`() {
        val policy = globalPolicy()

        // 12_345 * 0.08 = 987.6, * 0.028 = 345.66
        assertEquals(987L, policy.platformFeeOf(12_345L))
        assertEquals(345L, policy.pgFeeOf(12_345L))
    }

    @Test
    fun `금액이 0이면 수수료도 0이다`() {
        val policy = globalPolicy()

        assertEquals(0L, policy.platformFeeOf(0L))
        assertEquals(0L, policy.pgFeeOf(0L))
    }

    @Test
    fun `음수 금액에는 수수료를 매기지 않는다`() {
        val exception = assertFailsWith<BusinessException> { globalPolicy().platformFeeOf(-1L) }

        assertEquals(SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT, exception.errorCode)
    }

    @Test
    fun `셀러 전용 정책은 전역이 아니다`() {
        val policy = SettlementFeePolicy.of(42L, LocalDate.of(2026, 4, 1), BigDecimal("0.0500"), BigDecimal("0.0280"))

        assertFalse(policy.isGlobal())
        assertEquals(5_000L, policy.platformFeeOf(100_000L))
    }

    @Test
    fun `0과 1 밖의 요율은 정책이 되지 못한다`() {
        assertEquals(
            SettlementErrorCode.INVALID_SETTLEMENT_FEE_RATE,
            assertFailsWith<BusinessException> { policyWithRate(BigDecimal("-0.0001")) }.errorCode,
        )
        assertEquals(
            SettlementErrorCode.INVALID_SETTLEMENT_FEE_RATE,
            assertFailsWith<BusinessException> { policyWithRate(BigDecimal("1.0001")) }.errorCode,
        )
    }

    @Test
    fun `소수 넷째 자리를 넘는 요율은 정책이 되지 못한다`() {
        // DECIMAL(5,4)에 저장되면 0.0801로 반올림돼, 저장 전 8005원과 저장 후 8010원이 갈린다.
        val exception = assertFailsWith<BusinessException> { policyWithRate(BigDecimal("0.08005")) }

        assertEquals(SettlementErrorCode.INVALID_SETTLEMENT_FEE_RATE, exception.errorCode)
    }

    @Test
    fun `뒤에 0만 더 붙은 요율은 그대로 받는다`() {
        val policy = policyWithRate(BigDecimal("0.080000"))

        assertEquals(8_000L, policy.platformFeeOf(100_000L))
    }

    private fun globalPolicy(): SettlementFeePolicy =
        SettlementFeePolicy.of(null, LocalDate.of(2026, 1, 1), BigDecimal("0.0800"), BigDecimal("0.0280"))

    private fun policyWithRate(rate: BigDecimal): SettlementFeePolicy =
        SettlementFeePolicy.of(null, LocalDate.of(2026, 1, 1), rate, BigDecimal("0.0280"))
}
