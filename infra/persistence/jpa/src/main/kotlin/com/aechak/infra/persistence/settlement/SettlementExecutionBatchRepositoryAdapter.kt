package com.aechak.infra.persistence.settlement

import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import com.aechak.domain.settlement.batch.repository.SettlementExecutionBatchRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

interface SettlementExecutionBatchJpaRepository : JpaRepository<SettlementExecutionBatch, Long> {
    fun findBySettlementBaseDate(settlementBaseDate: LocalDate): SettlementExecutionBatch?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SettlementExecutionBatch b where b.settlementBaseDate = :baseDate")
    fun findBySettlementBaseDateForUpdate(
        @Param("baseDate") baseDate: LocalDate,
    ): SettlementExecutionBatch?
}

@Repository
class SettlementExecutionBatchRepositoryAdapter(
    private val jpaRepository: SettlementExecutionBatchJpaRepository,
) : SettlementExecutionBatchRepository {
    override fun save(batch: SettlementExecutionBatch): SettlementExecutionBatch = jpaRepository.save(batch)

    override fun findBySettlementBaseDate(settlementBaseDate: LocalDate): SettlementExecutionBatch? =
        jpaRepository.findBySettlementBaseDate(settlementBaseDate)

    override fun findBySettlementBaseDateForUpdate(settlementBaseDate: LocalDate): SettlementExecutionBatch? =
        jpaRepository.findBySettlementBaseDateForUpdate(settlementBaseDate)
}
