package com.aechak.infra.persistence.settlement

import com.aechak.domain.settlement.settlement.Settlement
import com.aechak.domain.settlement.settlement.repository.SettlementRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface SettlementJpaRepository : JpaRepository<Settlement, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Settlement?
}

@Repository
class SettlementRepositoryAdapter(
    private val jpaRepository: SettlementJpaRepository,
) : SettlementRepository {
    override fun saveAll(settlements: List<Settlement>): List<Settlement> = jpaRepository.saveAll(settlements)

    override fun findByIdempotencyKey(idempotencyKey: String): Settlement? = jpaRepository.findByIdempotencyKey(idempotencyKey)
}
