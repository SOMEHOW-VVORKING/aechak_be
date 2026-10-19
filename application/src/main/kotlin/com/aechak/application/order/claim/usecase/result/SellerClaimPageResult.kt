package com.aechak.application.order.claim.usecase.result

import com.aechak.application.support.OffsetPageResult
import com.aechak.domain.order.claim.enums.ClaimType

/**
 * 셀러 클레임 목록 + 유형별 건수. counts는 유형 필터를 무시하고 상태·기간 필터만 반영한다 —
 * 화면의 유형 탭 배지가 지금 고른 탭과 무관해야 하기 때문이다. 세 유형 키는 0이어도 빠지지 않는다.
 */
data class SellerClaimPageResult(
    val page: OffsetPageResult<SellerClaimSummaryResult>,
    val counts: Map<ClaimType, Long>,
) {
    companion object {
        fun of(
            page: OffsetPageResult<SellerClaimSummaryResult>,
            counts: Map<ClaimType, Long>,
        ): SellerClaimPageResult =
            SellerClaimPageResult(
                page = page,
                counts = ClaimType.entries.associateWith { counts[it] ?: 0L },
            )
    }
}
