package com.aechak.domain.order.claim.repository

import com.aechak.domain.order.claim.ClaimIntervention

interface ClaimInterventionRepository {
    fun save(intervention: ClaimIntervention): ClaimIntervention

    fun findByClaimId(claimId: Long): ClaimIntervention?
}
