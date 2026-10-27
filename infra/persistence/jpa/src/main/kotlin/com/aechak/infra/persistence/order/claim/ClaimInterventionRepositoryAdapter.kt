package com.aechak.infra.persistence.order.claim

import com.aechak.domain.order.claim.ClaimIntervention
import com.aechak.domain.order.claim.repository.ClaimInterventionRepository
import com.aechak.domain.order.claim.repository.DuplicateClaimInterventionException
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ClaimInterventionJpaRepository : JpaRepository<ClaimIntervention, Long> {
    fun findByClaimId(claimId: Long): ClaimIntervention?
}

@Repository
class ClaimInterventionRepositoryAdapter(
    private val jpaRepository: ClaimInterventionJpaRepository,
) : ClaimInterventionRepository {
    /** `claim_id` UNIQUE 위반만 업무 예외로 옮긴다 — 나머지 제약 위반은 그대로 올려 보낸다. */
    override fun save(intervention: ClaimIntervention): ClaimIntervention =
        try {
            jpaRepository.saveAndFlush(intervention)
        } catch (e: DataIntegrityViolationException) {
            val violation =
                generateSequence<Throwable>(e) { it.cause }
                    .filterIsInstance<ConstraintViolationException>()
                    .firstOrNull()

            if (
                violation?.kind == ConstraintViolationException.ConstraintKind.UNIQUE &&
                violation.constraintName?.substringAfterLast('.') == ClaimIntervention.UK_CLAIM_ID
            ) {
                throw DuplicateClaimInterventionException(e)
            }
            throw e
        }

    override fun findByClaimId(claimId: Long): ClaimIntervention? = jpaRepository.findByClaimId(claimId)
}
