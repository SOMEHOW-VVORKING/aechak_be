package com.aechak.application.order.claim.admin.facade

import com.aechak.application.order.claim.admin.service.AdminClaimInterventionService
import com.aechak.application.order.claim.admin.service.AdminClaimQueryService
import com.aechak.application.order.claim.admin.usecase.AdminClaimUseCase
import com.aechak.application.order.claim.admin.usecase.command.InterveneClaimCommand
import com.aechak.application.order.claim.admin.usecase.query.AdminClaimSearchQuery
import com.aechak.application.order.claim.admin.usecase.result.AdminClaimDetailResult
import com.aechak.application.order.claim.admin.usecase.result.AdminClaimPageResult
import com.aechak.application.order.claim.admin.usecase.result.ClaimInterventionReasonCatalogResult
import com.aechak.application.order.claim.admin.usecase.result.InterveneClaimResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class AdminClaimFacade(
    private val adminClaimQueryService: AdminClaimQueryService,
    private val adminClaimInterventionService: AdminClaimInterventionService,
) : AdminClaimUseCase {
    @Transactional(readOnly = true)
    override fun searchClaims(query: AdminClaimSearchQuery): AdminClaimPageResult = adminClaimQueryService.searchClaims(query)

    @Transactional(readOnly = true)
    override fun getClaim(claimPublicId: String): AdminClaimDetailResult = adminClaimQueryService.getClaim(claimPublicId)

    /** 고정 어휘라 DB를 보지 않아 트랜잭션이 없다. */
    override fun getInterventionReasons(): ClaimInterventionReasonCatalogResult = ClaimInterventionReasonCatalogResult.of()

    /**
     * READ_COMMITTED인 이유: 거부된 클레임을 뒤집을 때 그 사이 커밋된 다른 클레임을 봐야 한다.
     * MySQL 기본 격리수준에서는 트랜잭션 첫 조회 시점의 스냅샷을 계속 읽어 그 클레임을 놓치고,
     * 한 주문품목을 두 클레임이 붙잡은 상태로 커밋된다(클레임 요청 경로와 같은 이유).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    override fun interveneClaim(command: InterveneClaimCommand): InterveneClaimResult =
        adminClaimInterventionService.interveneClaim(command)
}
