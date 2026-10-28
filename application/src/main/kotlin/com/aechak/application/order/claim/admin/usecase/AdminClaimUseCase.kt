package com.aechak.application.order.claim.admin.usecase

import com.aechak.application.order.claim.admin.usecase.command.InterveneClaimCommand
import com.aechak.application.order.claim.admin.usecase.query.AdminClaimSearchQuery
import com.aechak.application.order.claim.admin.usecase.result.AdminClaimDetailResult
import com.aechak.application.order.claim.admin.usecase.result.AdminClaimPageResult
import com.aechak.application.order.claim.admin.usecase.result.ClaimInterventionReasonCatalogResult
import com.aechak.application.order.claim.admin.usecase.result.InterveneClaimResult

/**
 * 운영자 클레임 조회·개입. 자격(role=ADMIN) 게이트는 어드민 모듈 SecurityConfig가 전역 보증하므로
 * 이 계약은 자격을 다시 묻지 않는다 — 셀러·구매자 경로와 달리 소유자 범위로 좁히지도 않는다.
 */
interface AdminClaimUseCase {
    fun searchClaims(query: AdminClaimSearchQuery): AdminClaimPageResult

    fun getClaim(claimPublicId: String): AdminClaimDetailResult

    /** 귀책 판정별 개입 사유 어휘. */
    fun getInterventionReasons(): ClaimInterventionReasonCatalogResult

    /** 셀러 처리를 덮고 분쟁을 종결한다. 환불 집행·결제 취소는 건드리지 않는다. */
    fun interveneClaim(command: InterveneClaimCommand): InterveneClaimResult
}
