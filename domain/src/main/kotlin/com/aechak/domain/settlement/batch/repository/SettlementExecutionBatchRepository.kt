package com.aechak.domain.settlement.batch.repository

import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import java.time.LocalDate

interface SettlementExecutionBatchRepository {
    fun save(batch: SettlementExecutionBatch): SettlementExecutionBatch

    /** 기준일 배치 단건 조회 — 같은 기준일로 배치를 두 번 만들지 않기 위한 선조회. */
    fun findBySettlementBaseDate(settlementBaseDate: LocalDate): SettlementExecutionBatch?
}
