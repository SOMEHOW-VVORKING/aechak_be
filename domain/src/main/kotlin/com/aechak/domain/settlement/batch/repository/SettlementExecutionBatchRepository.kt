package com.aechak.domain.settlement.batch.repository

import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import java.time.LocalDate

interface SettlementExecutionBatchRepository {
    fun save(batch: SettlementExecutionBatch): SettlementExecutionBatch

    /** 기준일 배치 단건 조회 — 같은 기준일로 배치를 두 번 만들지 않기 위한 선조회. */
    fun findBySettlementBaseDate(settlementBaseDate: LocalDate): SettlementExecutionBatch?

    /**
     * 기준일 배치를 잠그고 읽는다 — 지급 건 생성과 회차 종결이 같은 배치 행에서 직렬화된다.
     * 잠그지 않으면 종결 직전에 통과한 지급 건이 종결된 회차에 붙는다.
     */
    fun findBySettlementBaseDateForUpdate(settlementBaseDate: LocalDate): SettlementExecutionBatch?
}
