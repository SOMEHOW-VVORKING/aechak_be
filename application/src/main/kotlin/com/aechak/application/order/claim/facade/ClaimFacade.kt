package com.aechak.application.order.claim.facade

import com.aechak.application.order.claim.service.ClaimCollectionService
import com.aechak.application.order.claim.service.ClaimService
import com.aechak.application.order.claim.service.SellerClaimQueryService
import com.aechak.application.order.claim.usecase.ClaimUseCase
import com.aechak.application.order.claim.usecase.SellerClaimUseCase
import com.aechak.application.order.claim.usecase.command.ApproveClaimCommand
import com.aechak.application.order.claim.usecase.command.CompleteClaimCollectionCommand
import com.aechak.application.order.claim.usecase.command.RejectClaimCommand
import com.aechak.application.order.claim.usecase.command.RequestClaimCommand
import com.aechak.application.order.claim.usecase.command.ReshipClaimCommand
import com.aechak.application.order.claim.usecase.command.StartClaimCollectionCommand
import com.aechak.application.order.claim.usecase.command.UpdateClaimMemoCommand
import com.aechak.application.order.claim.usecase.command.WithdrawClaimCommand
import com.aechak.application.order.claim.usecase.query.SellerClaimMetricsQuery
import com.aechak.application.order.claim.usecase.query.SellerClaimSearchQuery
import com.aechak.application.order.claim.usecase.result.ClaimListResult
import com.aechak.application.order.claim.usecase.result.ClaimReasonCatalogResult
import com.aechak.application.order.claim.usecase.result.RequestClaimResult
import com.aechak.application.order.claim.usecase.result.ReshipClaimResult
import com.aechak.application.order.claim.usecase.result.SellerClaimDetailResult
import com.aechak.application.order.claim.usecase.result.SellerClaimMetricsResult
import com.aechak.application.order.claim.usecase.result.SellerClaimPageResult
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.seller.seller.enums.SellerStatus
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
    private val claimCollectionService: ClaimCollectionService,
    private val sellerClaimQueryService: SellerClaimQueryService,
    private val sellerUseCase: SellerUseCase,
) : ClaimUseCase,
    SellerClaimUseCase {
    /**
     * READ_COMMITTED인 이유: 중복 클레임 검사가 주문 행 선점 뒤에 커밋된 다른 요청의 클레임을 봐야 한다.
     * MySQL 기본 격리수준에서는 트랜잭션 첫 조회 시점의 스냅샷을 계속 읽어 그 클레임을 놓친다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun requestClaim(command: RequestClaimCommand): RequestClaimResult = claimService.requestClaim(command)

    /** 고정 어휘라 DB를 보지 않아 트랜잭션이 없다. */
    override fun getClaimReasons(): ClaimReasonCatalogResult = ClaimReasonCatalogResult.of()

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

    @Transactional(readOnly = true)
    override fun searchClaims(query: SellerClaimSearchQuery): SellerClaimPageResult {
        requireActiveSeller(query.sellerId)
        return sellerClaimQueryService.searchClaims(query)
    }

    @Transactional(readOnly = true)
    override fun getClaim(
        claimPublicId: String,
        sellerId: Long,
    ): SellerClaimDetailResult {
        requireActiveSeller(sellerId)
        return sellerClaimQueryService.getClaim(claimPublicId, sellerId)
    }

    @Transactional
    override fun updateSellerMemo(command: UpdateClaimMemoCommand) {
        requireActiveSeller(command.sellerId)
        claimService.updateSellerMemo(command)
    }

    @Transactional
    override fun startCollection(command: StartClaimCollectionCommand) {
        requireActiveSeller(command.sellerId)
        claimCollectionService.startCollection(command)
    }

    @Transactional
    override fun completeCollection(command: CompleteClaimCollectionCommand) {
        requireActiveSeller(command.sellerId)
        claimCollectionService.completeCollection(command)
    }

    @Transactional
    override fun reship(command: ReshipClaimCommand): ReshipClaimResult {
        requireActiveSeller(command.sellerId)
        return claimCollectionService.reship(command)
    }

    /** 자격 게이트 없음 — 계약 KDoc대로 호출자(대시보드 조립)가 이미 통과시킨 sellerId만 들어온다. */
    @Transactional(readOnly = true)
    override fun getClaimMetrics(query: SellerClaimMetricsQuery): SellerClaimMetricsResult = sellerClaimQueryService.countMetrics(query)

    /** 셀러가 아니면 seller BC가 10004로 거른다 — 여기 코드는 등록된 셀러의 상태 정책만 가린다 */
    private fun requireActiveSeller(sellerId: Long) {
        if (sellerUseCase.requireSellerStatus(sellerId) != SellerStatus.ACTIVE) {
            throw BusinessException(OrderErrorCode.CLAIM_ACCESS_DENIED)
        }
    }
}
