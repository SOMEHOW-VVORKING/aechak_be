package com.aechak.application.order.claim.usecase

import com.aechak.application.order.claim.usecase.command.RequestClaimCommand
import com.aechak.application.order.claim.usecase.command.WithdrawClaimCommand
import com.aechak.application.order.claim.usecase.result.ClaimListResult
import com.aechak.application.order.claim.usecase.result.RequestClaimResult

/**
 * 구매자가 쓰는 클레임 진입점 계약. 셀러센터가 쓰는 SellerClaimUseCase와 나눠 둔다 —
 * 소비하는 실행 모듈이 갈리고 메서드가 겹치지 않는다.
 */
interface ClaimUseCase {
    /** 클레임 요청 — 환불액을 확정해 claim_items에 저장하고 자동승인 기한을 부여한다. */
    fun requestClaim(command: RequestClaimCommand): RequestClaimResult

    fun getClaims(
        orderPublicId: String,
        buyerId: Long,
    ): ClaimListResult

    /** 셀러가 판단하기 전까지만 거둘 수 있다. */
    fun withdrawClaim(command: WithdrawClaimCommand)
}
