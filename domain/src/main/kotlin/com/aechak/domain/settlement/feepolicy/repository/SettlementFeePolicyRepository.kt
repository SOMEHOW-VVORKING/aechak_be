package com.aechak.domain.settlement.feepolicy.repository

import com.aechak.domain.settlement.feepolicy.SettlementFeePolicy
import java.time.LocalDate

interface SettlementFeePolicyRepository {
    /**
     * 기준일에 적용할 정책 1건. 셀러 전용 정책이 전역 정책을 덮고, 같은 범위에서는 적용 시작일이 늦은 행이 이긴다.
     */
    fun findEffective(
        sellerId: Long,
        on: LocalDate,
    ): SettlementFeePolicy?
}
