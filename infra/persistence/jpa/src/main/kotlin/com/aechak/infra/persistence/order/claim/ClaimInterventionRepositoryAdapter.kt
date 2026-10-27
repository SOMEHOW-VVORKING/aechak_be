package com.aechak.infra.persistence.order.claim

import com.aechak.domain.order.claim.ClaimIntervention
import com.aechak.domain.order.claim.repository.ClaimInterventionRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ClaimInterventionJpaRepository : JpaRepository<ClaimIntervention, Long> {
    fun findByClaimId(claimId: Long): ClaimIntervention?
}

@Repository
class ClaimInterventionRepositoryAdapter(
    private val jpaRepository: ClaimInterventionJpaRepository,
) : ClaimInterventionRepository {
    override fun save(intervention: ClaimIntervention): ClaimIntervention = jpaRepository.save(intervention)

    override fun findByClaimId(claimId: Long): ClaimIntervention? = jpaRepository.findByClaimId(claimId)
}
