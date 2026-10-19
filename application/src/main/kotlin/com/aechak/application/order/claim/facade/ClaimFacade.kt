package com.aechak.application.order.claim.facade

import com.aechak.application.order.claim.service.ClaimService
import com.aechak.application.order.claim.usecase.ClaimUseCase
import com.aechak.application.order.claim.usecase.SellerClaimUseCase
import com.aechak.application.order.claim.usecase.command.ApproveClaimCommand
import com.aechak.application.order.claim.usecase.command.RejectClaimCommand
import com.aechak.application.order.claim.usecase.command.RequestClaimCommand
import com.aechak.application.order.claim.usecase.command.WithdrawClaimCommand
import com.aechak.application.order.claim.usecase.result.ClaimListResult
import com.aechak.application.order.claim.usecase.result.RequestClaimResult
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

/**
 * 구매자·셀러 양쪽 클레임 계약의 유일한 구현체. 트랜잭션 경계는 여기 고정.
 * 승인은 상태만 바꾼다 — 환불 집행은 이 파사드의 범위가 아니라 refundStatus가 NONE으로 남는다.
 */
@Service
class ClaimFacade(
    private val claimService: ClaimService,
    private val sellerUseCase: SellerUseCase,
) : ClaimUseCase,
    SellerClaimUseCase {
    /**
     * READ_COMMITTED인 이유: 중복 클레임 검사가 주문 행 선점 뒤에 커밋된 다른 요청의 클레임을 봐야 한다.
     * MySQL 기본 격리수준에서는 트랜잭션 첫 조회 시점의 스냅샷을 계속 읽어 그 클레임을 놓친다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun requestClaim(command: RequestClaimCommand): RequestClaimResult = claimService.requestClaim(command)

    @Transactional(readOnly = true)
    override fun getClaims(
        orderPublicId: String,
        buyerId: Long,
    ): ClaimListResult = claimService.getClaims(orderPublicId, buyerId)

    @Transactional
    override fun withdrawClaim(command: WithdrawClaimCommand) = claimService.withdrawClaim(command)

    @Transactional
    override fun approveClaim(command: ApproveClaimCommand) {
        requireActiveSeller(command.sellerId)
        claimService.approveClaim(command)
    }

    @Transactional
    override fun rejectClaim(command: RejectClaimCommand) {
        requireActiveSeller(command.sellerId)
        claimService.rejectClaim(command)
    }

    private fun requireActiveSeller(sellerId: Long) {
        if (!sellerUseCase.isActiveSeller(sellerId)) {
            throw BusinessException(OrderErrorCode.CLAIM_ACCESS_DENIED)
        }
    }
}
