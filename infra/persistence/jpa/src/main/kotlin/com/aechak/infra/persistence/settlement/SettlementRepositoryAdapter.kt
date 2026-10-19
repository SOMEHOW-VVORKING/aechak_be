package com.aechak.infra.persistence.settlement

import com.aechak.domain.settlement.settlement.Settlement
import com.aechak.domain.settlement.settlement.enums.SettlementStatus
import com.aechak.domain.settlement.settlement.repository.SettlementRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

interface SettlementJpaRepository : JpaRepository<Settlement, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Settlement?

    fun findAllByIdempotencyKeyIn(idempotencyKeys: List<String>): List<Settlement>

    /** payout이 null인 행만 — 이미 지급 묶음에 들어간 원장을 다음 회차가 다시 집으면 같은 돈을 두 번 내보낸다. */
    @Query(
        "select s from Settlement s " +
            "where s.status = :status and s.payout is null and s.expectedPayoutDate <= :baseDate " +
            "order by s.id asc",
    )
    fun findAllPayoutCandidates(
        @Param("status") status: SettlementStatus,
        @Param("baseDate") baseDate: LocalDate,
    ): List<Settlement>

    @Query(
        "select s from Settlement s " +
            "where s.status = :status and s.payout is null and s.sellerId = :sellerId and s.expectedPayoutDate <= :baseDate " +
            "order by s.id asc",
    )
    fun findAllPayoutCandidatesBySellerId(
        @Param("status") status: SettlementStatus,
        @Param("sellerId") sellerId: Long,
        @Param("baseDate") baseDate: LocalDate,
    ): List<Settlement>
}

@Repository
class SettlementRepositoryAdapter(
    private val jpaRepository: SettlementJpaRepository,
) : SettlementRepository {
    override fun saveAll(settlements: List<Settlement>): List<Settlement> = jpaRepository.saveAll(settlements)

    override fun findByIdempotencyKey(idempotencyKey: String): Settlement? = jpaRepository.findByIdempotencyKey(idempotencyKey)

    override fun findAllByIdempotencyKeyIn(idempotencyKeys: List<String>): List<Settlement> =
        jpaRepository.findAllByIdempotencyKeyIn(idempotencyKeys)

    override fun findAllPayoutCandidates(baseDate: LocalDate): List<Settlement> =
        jpaRepository.findAllPayoutCandidates(SettlementStatus.PENDING, baseDate)

    override fun findAllPayoutCandidatesBySellerId(
        sellerId: Long,
        baseDate: LocalDate,
    ): List<Settlement> = jpaRepository.findAllPayoutCandidatesBySellerId(SettlementStatus.PENDING, sellerId, baseDate)
}
