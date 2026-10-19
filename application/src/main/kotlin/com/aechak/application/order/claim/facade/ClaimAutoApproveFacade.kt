package com.aechak.application.order.claim.facade

import com.aechak.application.order.claim.service.ClaimAutoApproveService
import com.aechak.application.order.claim.usecase.ClaimAutoApproveUseCase
import com.aechak.application.order.claim.usecase.command.AutoApproveClaimCommand
import com.aechak.application.order.claim.usecase.result.AutoApprovableClaimResult
import com.aechak.application.order.claim.usecase.result.AutoApproveClaimResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 자동승인 계약의 유일한 구현체. 셀러 자격 검증도 결제·PG도 주입받지 않아 ClaimFacade·ClaimRefundFacade와 파사드를 갈랐다 —
 * 소비 모듈이 배치뿐이고 주입 의존이 그 둘과 겹치지 않는다.
 *
 * 승인과 환불을 한 경계에 묶지 않는다. 환불 집행은 PG 호출을 가운데 두고 트랜잭션을 셋으로 쪼개므로
 * 승인 트랜잭션에 얹으면 승인이 PG 응답만큼 커넥션을 붙잡는다. 배치가 두 계약을 순서대로 부른다.
 */
@Service
class ClaimAutoApproveFacade(
    private val claimAutoApproveService: ClaimAutoApproveService,
) : ClaimAutoApproveUseCase {
    @Transactional(readOnly = true)
    override fun findAutoApprovableClaims(limit: Int): List<AutoApprovableClaimResult> = claimAutoApproveService.findAutoApprovable(limit)

    @Transactional
    override fun autoApprove(command: AutoApproveClaimCommand): AutoApproveClaimResult =
        claimAutoApproveService.autoApprove(command.claimPublicId)
}
