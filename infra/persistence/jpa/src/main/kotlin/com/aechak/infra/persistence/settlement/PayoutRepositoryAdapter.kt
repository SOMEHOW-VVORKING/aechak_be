package com.aechak.infra.persistence.settlement

import com.aechak.domain.settlement.payout.Payout
import com.aechak.domain.settlement.payout.repository.PayoutRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface PayoutJpaRepository : JpaRepository<Payout, Long> {
    fun findByTransferKey(transferKey: String): Payout?
}

@Repository
class PayoutRepositoryAdapter(
    private val jpaRepository: PayoutJpaRepository,
) : PayoutRepository {
    override fun save(payout: Payout): Payout = jpaRepository.save(payout)

    override fun findByTransferKey(transferKey: String): Payout? = jpaRepository.findByTransferKey(transferKey)
}
