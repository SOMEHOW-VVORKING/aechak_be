package com.aechak.application.order.claim.service

import com.aechak.application.order.claim.usecase.result.AutoApprovableClaimResult
import com.aechak.application.order.claim.usecase.result.AutoApproveClaimResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.repository.ClaimRepository
import com.aechak.domain.order.error.OrderErrorCode
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 자동승인 본체 — Facade에서만 호출된다.
 * 셀러 스코프로 클레임을 찾지 않는다는 점만 셀러 승인과 다르다. 승인 주체가 시스템이라 자격을 대조할 셀러가 없다.
 */
@Service
class ClaimAutoApproveService(
    private val claimRepository: ClaimRepository,
) {
    fun findAutoApprovable(limit: Int): List<AutoApprovableClaimResult> =
        claimRepository.findAutoApprovable(LocalDateTime.now(), limit).map(AutoApprovableClaimResult::from)

    /** 기한을 승인 트랜잭션에서 다시 확인한다 — 스캔 결과가 오래됐거나 그 사이 셀러가 처리했으면 건너뛴다. */
    fun autoApprove(claimPublicId: String): AutoApproveClaimResult {
        val claim =
            claimRepository.findByPublicId(claimPublicId)
                ?: throw BusinessException(OrderErrorCode.CLAIM_NOT_FOUND)
        if (!claim.isAutoApprovable(LocalDateTime.now())) return AutoApproveClaimResult.skipped()
        claim.approveByTimeout()
        claimRepository.save(claim)
        return AutoApproveClaimResult.approved()
    }
}
