package com.aechak.application.order.claim.admin.service

import com.aechak.application.order.claim.admin.usecase.command.InterveneClaimCommand
import com.aechak.application.order.claim.admin.usecase.result.InterveneClaimResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.repository.ClaimInterventionRepository
import com.aechak.domain.order.claim.repository.ClaimRepository
import com.aechak.domain.order.error.OrderErrorCode
import org.springframework.stereotype.Service

/**
 * 운영자 개입 실행. 뒤집기와 개입 기록은 애그리거트가 한 호출로 내므로 여기서는
 * 애그리거트가 볼 수 없는 것 — 같은 주문품목을 다른 클레임이 이미 붙잡고 있는지 — 만 더 본다.
 */
@Service
class AdminClaimInterventionService(
    private val claimRepository: ClaimRepository,
    private val claimInterventionRepository: ClaimInterventionRepository,
) {
    fun interveneClaim(command: InterveneClaimCommand): InterveneClaimResult {
        val claim =
            claimRepository.findByPublicId(command.claimPublicId)
                ?: throw BusinessException(OrderErrorCode.CLAIM_NOT_FOUND)
        requireNoCompetingClaim(claim)
        val intervention =
            claim.overrideByAdmin(
                adminId = command.adminId,
                faultDecision = command.faultDecision,
                reason = command.reason,
                reasonDetail = command.reasonDetail,
                returnCostBearer = command.returnCostBearer,
                internalMemo = command.internalMemo,
            )
        claimRepository.save(claim)
        claimInterventionRepository.save(intervention)
        return InterveneClaimResult.of(claim, intervention)
    }

    /**
     * 거부·철회된 클레임은 대상 주문품목을 풀어 줘(ClaimStatus.RELEASED) 그 품목에 새 클레임이 걸릴 수 있다.
     * 뒤집으면 품목을 다시 붙잡는 상태로 돌아가므로, 그 사이에 생긴 클레임이 있으면 한 품목을 두 클레임이
     * 동시에 붙잡게 된다 — 중복 클레임 차단 질의가 바로 깨지고 환불 집행이 같은 돈을 두 번 셀 수 있다.
     *
     * 품목을 놓지 않은 상태(요청·회수 중·승인)에서 뒤집는 것은 이 위험이 없어 질의를 내지 않는다.
     */
    private fun requireNoCompetingClaim(claim: Claim) {
        if (claim.status !in ClaimStatus.RELEASED) return
        val taken = claim.items.any { claimRepository.existsActiveByOrderItemId(it.orderItem.id) }
        if (taken) {
            throw BusinessException(OrderErrorCode.CLAIM_ALREADY_IN_PROGRESS)
        }
    }
}
