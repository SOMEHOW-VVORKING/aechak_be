package com.aechak.application.order.claim.usecase

import com.aechak.application.order.claim.usecase.command.ApproveClaimCommand
import com.aechak.application.order.claim.usecase.command.RejectClaimCommand
import com.aechak.application.order.claim.usecase.command.UpdateClaimMemoCommand
import com.aechak.application.order.claim.usecase.query.SellerClaimMetricsQuery
import com.aechak.application.order.claim.usecase.query.SellerClaimSearchQuery
import com.aechak.application.order.claim.usecase.result.SellerClaimDetailResult
import com.aechak.application.order.claim.usecase.result.SellerClaimMetricsResult
import com.aechak.application.order.claim.usecase.result.SellerClaimPageResult

interface SellerClaimUseCase {
    /** 승인만 한다 — 환불 집행은 별도 경로다. */
    fun approveClaim(command: ApproveClaimCommand)

    fun rejectClaim(command: RejectClaimCommand)

    fun searchClaims(query: SellerClaimSearchQuery): SellerClaimPageResult

    fun getClaim(
        claimPublicId: String,
        sellerId: Long,
    ): SellerClaimDetailResult

    fun updateSellerMemo(command: UpdateClaimMemoCommand)

    /**
     * 대시보드용 클레임 지표 집계 — 셀러 미응답(요청 상태) 건을 유형별로 쿼리 한 번에 센다.
     *
     * 전제 — 셀러 자격 게이트는 호출자가 통과시킨다. 집계는 넘겨받은 sellerId 범위만 읽고
     * 식별자를 돌려주지 않으므로 소유권 판정 대상이 아니다. 이 계약을 HTTP로 직접 노출하지 않는다.
     */
    fun getClaimMetrics(query: SellerClaimMetricsQuery): SellerClaimMetricsResult
}
