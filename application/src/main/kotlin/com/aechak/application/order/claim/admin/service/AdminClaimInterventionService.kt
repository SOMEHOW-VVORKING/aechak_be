package com.aechak.application.order.claim.admin.service

import com.aechak.application.order.claim.admin.usecase.command.InterveneClaimCommand
import com.aechak.application.order.claim.admin.usecase.result.InterveneClaimResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimIntervention
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.repository.ClaimInterventionRepository
import com.aechak.domain.order.claim.repository.ClaimRepository
import com.aechak.domain.order.claim.repository.DuplicateClaimInterventionException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.repository.OrderRepository
import org.springframework.stereotype.Service

/**
 * 운영자 개입 실행. 뒤집기와 개입 기록은 애그리거트가 한 호출로 내므로 여기서는
 * 애그리거트가 볼 수 없는 것 — 같은 주문품목을 다른 클레임이 이미 붙잡고 있는지 — 만 더 본다.
 */
@Service
class AdminClaimInterventionService(
    private val claimRepository: ClaimRepository,
    private val claimInterventionRepository: ClaimInterventionRepository,
    private val orderRepository: OrderRepository,
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
        saveRecord(intervention)
        return InterveneClaimResult.of(claim, intervention)
    }

    /**
     * 거부·철회된 클레임은 대상 주문품목을 풀어 줘(ClaimStatus.RELEASED) 그 품목에 새 클레임이 걸릴 수 있다.
     * 뒤집으면 품목을 다시 붙잡는 상태로 돌아가므로, 그 사이에 생긴 클레임이 있으면 한 품목을 두 클레임이
     * 동시에 붙잡게 된다 — 중복 클레임 차단 질의가 바로 깨지고 환불 집행이 같은 돈을 두 번 셀 수 있다.
     *
     * 검사 전에 주문 행을 선점한다 — 클레임 요청 경로(`ClaimService.requestClaim`)가 잠그는 것과 같은 행이다.
     * 잠그지 않으면 겹친 신규 요청과 뒤집기가 둘 다 "진행 중 없음"을 읽고 둘 다 커밋한다.
     *
     * 품목을 놓지 않은 상태(요청·회수 중·승인)에서 뒤집는 것은 이 위험이 없어 잠금도 질의도 내지 않는다.
     */
    private fun requireNoCompetingClaim(claim: Claim) {
        if (claim.status !in ClaimStatus.RELEASED) return
        orderRepository.findByIdForUpdate(claim.orderId)
            ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
        val taken = claim.items.any { claimRepository.existsActiveByOrderItemId(it.orderItem.id) }
        if (taken) {
            throw BusinessException(OrderErrorCode.CLAIM_ALREADY_IN_PROGRESS)
        }
    }

    /**
     * 기록 1행이 개입의 멱등 게이트다 — 겹친 두 개입은 상태 판정을 둘 다 통과하고
     * 늦은 쪽이 `claim_id` UNIQUE 에 걸린다. 순차 두 번째 개입과 같은 409로 접는다.
     */
    private fun saveRecord(intervention: ClaimIntervention) {
        try {
            claimInterventionRepository.save(intervention)
        } catch (e: DuplicateClaimInterventionException) {
            throw BusinessException(OrderErrorCode.CLAIM_NOT_OVERRIDABLE, e)
        }
    }
}
