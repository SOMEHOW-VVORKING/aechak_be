package com.aechak.application.order.claim.port

import com.aechak.application.order.claim.port.view.SellerClaimItemView
import com.aechak.application.order.claim.port.view.SellerClaimTypeCountView
import com.aechak.application.order.claim.port.view.SellerClaimView

interface SellerClaimQueryPort {
    fun findPage(condition: SellerClaimCondition): List<SellerClaimView>

    fun count(condition: SellerClaimCondition): Long

    /**
     * 유형별 건수. **유형 필터는 적용하지 않는다** — 화면의 유형 탭 배지가 지금 선택된 탭과
     * 무관하게 모든 탭의 건수를 보여야 한다. 상태·기간 필터는 그대로 반영한다.
     */
    fun countByClaimType(condition: SellerClaimCondition): List<SellerClaimTypeCountView>

    /** 셀러 범위 단건 — 타 셀러 클레임은 없는 것으로 본다. */
    fun findOne(
        claimPublicId: String,
        sellerId: Long,
    ): SellerClaimView?

    fun findItems(claimIds: Collection<Long>): List<SellerClaimItemView>

    /** 초회 발송이 끝난 주문 — 반품비 부담 문구가 "미발송"인지 가르는 근거. */
    fun findDispatchedOrderIds(orderIds: Collection<Long>): List<Long>
}
