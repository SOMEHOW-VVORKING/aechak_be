package com.aechak.infra.persistence.user.point

import com.aechak.domain.user.point.PointTransaction
import com.aechak.domain.user.point.repository.PointTransactionRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface PointTransactionJpaRepository : JpaRepository<PointTransaction, Long>

@Repository
class PointTransactionRepositoryAdapter(
    private val jpaRepository: PointTransactionJpaRepository,
) : PointTransactionRepository {
    override fun save(pointTransaction: PointTransaction): PointTransaction = jpaRepository.save(pointTransaction)
}
