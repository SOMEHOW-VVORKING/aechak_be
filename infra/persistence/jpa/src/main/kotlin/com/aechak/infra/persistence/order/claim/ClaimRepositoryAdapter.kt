package com.aechak.infra.persistence.order.claim

import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.policy.ClaimProgressPolicy
import com.aechak.domain.order.claim.repository.ClaimRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ClaimJpaRepository : JpaRepository<Claim, Long> {
    @Query("select count(c) > 0 from Claim c where c.orderId = :orderId and c.status in :statuses")
    fun existsByOrderIdAndStatusIn(
        @Param("orderId") orderId: Long,
        @Param("statuses") statuses: Collection<ClaimStatus>,
    ): Boolean
}

@Repository
class ClaimRepositoryAdapter(
    private val jpaRepository: ClaimJpaRepository,
) : ClaimRepository {
    override fun existsInProgressByOrderId(orderId: Long): Boolean =
        jpaRepository.existsByOrderIdAndStatusIn(orderId, ClaimProgressPolicy.IN_PROGRESS)
}
