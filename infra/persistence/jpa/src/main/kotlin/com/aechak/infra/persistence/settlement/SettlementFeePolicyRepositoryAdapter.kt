package com.aechak.infra.persistence.settlement

import com.aechak.domain.settlement.feepolicy.SettlementFeePolicy
import com.aechak.domain.settlement.feepolicy.repository.SettlementFeePolicyRepository
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

interface SettlementFeePolicyJpaRepository : JpaRepository<SettlementFeePolicy, Long> {
    /**
     * 셀러 전용 정책을 전역 정책보다 먼저 세우고, 같은 범위에서는 적용 시작일이 늦은 행을 먼저 세운다.
     * 전역 정책은 seller_id가 NULL이라 UNIQUE가 중복을 막지 못하므로 id까지 정렬에 넣어 같은 입력이면 같은 행을 돌려준다.
     */
    @Query(
        "select p from SettlementFeePolicy p " +
            "where (p.sellerId = :sellerId or p.sellerId is null) and p.effectiveFrom <= :on " +
            "order by case when p.sellerId is null then 1 else 0 end asc, p.effectiveFrom desc, p.id desc",
    )
    fun findEffective(
        @Param("sellerId") sellerId: Long,
        @Param("on") on: LocalDate,
        limit: Limit,
    ): List<SettlementFeePolicy>
}

@Repository
class SettlementFeePolicyRepositoryAdapter(
    private val jpaRepository: SettlementFeePolicyJpaRepository,
) : SettlementFeePolicyRepository {
    override fun findEffective(
        sellerId: Long,
        on: LocalDate,
    ): SettlementFeePolicy? = jpaRepository.findEffective(sellerId, on, Limit.of(1)).firstOrNull()
}
