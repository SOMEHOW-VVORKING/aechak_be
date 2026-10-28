package com.aechak.domain.settlement.account.repository

import com.aechak.domain.settlement.account.SettlementAccount

interface SettlementAccountRepository {
    fun save(account: SettlementAccount): SettlementAccount

    /** 여러 셀러의 정산계좌를 한 번에 조회한다 — 집행 배치가 계좌 검증 상태로 대상을 걸러내는 데 쓴다. */
    fun findAllBySellerIdIn(sellerIds: List<Long>): List<SettlementAccount>
}
