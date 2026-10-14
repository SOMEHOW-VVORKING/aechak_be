package com.aechak.infra.persistence.settlement

import com.aechak.domain.settlement.account.SettlementAccount
import com.aechak.domain.settlement.account.repository.SettlementAccountRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** Spring Data 인터페이스는 이 모듈 밖으로 노출되지 않는다 — 어댑터의 내부 부품. */
interface SettlementAccountJpaRepository : JpaRepository<SettlementAccount, Long>

@Repository
class SettlementAccountRepositoryAdapter(
    private val jpaRepository: SettlementAccountJpaRepository,
) : SettlementAccountRepository {
    override fun save(account: SettlementAccount): SettlementAccount = jpaRepository.save(account)
}
