package com.aechak.application.order.claim.admin.port

import com.aechak.application.order.claim.admin.port.view.AdminClaimInterventionView
import com.aechak.application.order.claim.admin.port.view.AdminClaimView
import com.aechak.application.order.claim.port.view.SellerClaimItemView

interface AdminClaimQueryPort {
    /** 커서 다음 페이지. 다음 페이지 유무 판정은 호출자가 limit + 1을 넘겨 센다. */
    fun findPage(condition: AdminClaimCondition): List<AdminClaimView>

    fun count(condition: AdminClaimCondition): Long

    /** 운영자 범위 단건 — 셀러 범위 제한이 없다. */
    fun findOne(claimPublicId: String): AdminClaimView?

    /**
     * 클레임 대상 품목. 셀러 목록과 같은 읽기 모델을 쓴다 — 같은 클레임의 품목·환불액이
     * 두 화면에서 갈리지 않게 투영을 하나로 둔다.
     */
    fun findItems(claimIds: Collection<Long>): List<SellerClaimItemView>

    fun findInterventions(claimIds: Collection<Long>): List<AdminClaimInterventionView>
}
