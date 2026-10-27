package com.aechak.application.order.claim.usecase

import com.aechak.application.order.claim.usecase.command.ApproveClaimCommand
import com.aechak.application.order.claim.usecase.command.CompleteClaimCollectionCommand
import com.aechak.application.order.claim.usecase.command.RejectClaimCommand
import com.aechak.application.order.claim.usecase.command.ReshipClaimCommand
import com.aechak.application.order.claim.usecase.command.StartClaimCollectionCommand
import com.aechak.application.order.claim.usecase.command.UpdateClaimMemoCommand
import com.aechak.application.order.claim.usecase.query.SellerClaimMetricsQuery
import com.aechak.application.order.claim.usecase.query.SellerClaimSearchQuery
import com.aechak.application.order.claim.usecase.result.ReshipClaimResult
import com.aechak.application.order.claim.usecase.result.SellerClaimDetailResult
import com.aechak.application.order.claim.usecase.result.SellerClaimMetricsResult
import com.aechak.application.order.claim.usecase.result.SellerClaimPageResult

interface SellerClaimUseCase {
    /** 승인만 한다 — 환불 집행은 별도 경로다. 반품·교환은 회수 대기로 넘어간다. */
    fun approveClaim(command: ApproveClaimCommand)

    fun rejectClaim(command: RejectClaimCommand)

    fun searchClaims(query: SellerClaimSearchQuery): SellerClaimPageResult

    fun getClaim(
        claimPublicId: String,
        sellerId: Long,
    ): SellerClaimDetailResult

    fun updateSellerMemo(command: UpdateClaimMemoCommand)

    /** 회수 송장 등록. */
    fun startCollection(command: StartClaimCollectionCommand)

    /** 회수 완료 — 반품 환불은 이 뒤에만 집행된다. */
    fun completeCollection(command: CompleteClaimCollectionCommand)

    /** 교환 재발송 송장 등록. */
    fun reship(command: ReshipClaimCommand): ReshipClaimResult

    /**
     * 대시보드용 클레임 지표 집계 — 셀러가 할 일이 남은 건을 단계별로 쿼리 한 번에 센다.
     * 요청 대기는 유형별로 갈리고, 승인 이후 남은 일(회수 송장 등록·회수 완료 처리·교환 재발송)은 단계로 갈린다.
     *
     * 전제 — 셀러 자격 게이트는 호출자가 통과시킨다. 집계는 넘겨받은 sellerId 범위만 읽고
     * 식별자를 돌려주지 않으므로 소유권 판정 대상이 아니다. 이 계약을 HTTP로 직접 노출하지 않는다.
     */
    fun getClaimMetrics(query: SellerClaimMetricsQuery): SellerClaimMetricsResult
}
