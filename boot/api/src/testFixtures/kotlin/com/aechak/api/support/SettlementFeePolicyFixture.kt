package com.aechak.api.support

import com.aechak.domain.settlement.feepolicy.SettlementFeePolicy
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 전역 수수료율 정책 픽스처. 구매확정이 정산 원장을 만들 때 요율 정책 1행을 요구하는데,
 * 통합 테스트는 매 테스트 전에 테이블을 비워 마이그레이션 시드가 남지 않는다 —
 * 확정을 태우는 테스트는 실 환경 시드와 같은 값의 행을 직접 심는다.
 */
object SettlementFeePolicyFixture {
    val PLATFORM_FEE_RATE: BigDecimal = BigDecimal("0.0800")
    val PG_FEE_RATE: BigDecimal = BigDecimal("0.0280")
    val EFFECTIVE_FROM: LocalDate = LocalDate.of(2026, 1, 1)

    fun global(): SettlementFeePolicy = SettlementFeePolicy.of(null, EFFECTIVE_FROM, PLATFORM_FEE_RATE, PG_FEE_RATE)
}
