package com.aechak.infra.persistence.settlement

import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import com.aechak.domain.settlement.batch.repository.SettlementExecutionBatchRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

interface SettlementExecutionBatchJpaRepository : JpaRepository<SettlementExecutionBatch, Long> {
    fun findBySettlementBaseDate(settlementBaseDate: LocalDate): SettlementExecutionBatch?
}

@Repository
class SettlementExecutionBatchRepositoryAdapter(
    private val jpaRepository: SettlementExecutionBatchJpaRepository,
) : SettlementExecutionBatchRepository {
    override fun save(batch: SettlementExecutionBatch): SettlementExecutionBatch = jpaRepository.save(batch)

    override fun findBySettlementBaseDate(settlementBaseDate: LocalDate): SettlementExecutionBatch? =
        jpaRepository.findBySettlementBaseDate(settlementBaseDate)
}
