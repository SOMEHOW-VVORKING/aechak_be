package com.aechak.domain.settlement.payout.repository

import com.aechak.domain.settlement.payout.Payout

interface PayoutRepository {
    fun save(payout: Payout): Payout

    /** 송금키 단건 조회 — 배치 재실행이 같은 셀러에게 지급을 두 번 만들지 않기 위한 선조회. */
    fun findByTransferKey(transferKey: String): Payout?

    /** 배치 하나가 만든 지급 건 수 — 지급 건이 하나도 없는 회차를 바로 종결 처리하는 데 쓴다. */
    fun countByBatchId(batchId: Long): Long
}
