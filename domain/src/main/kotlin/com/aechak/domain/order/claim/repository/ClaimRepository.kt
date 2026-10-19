package com.aechak.domain.order.claim.repository

import com.aechak.domain.order.claim.Claim
import java.time.LocalDateTime

interface ClaimRepository {
    /** 클레임 품목은 Claim의 cascade로 함께 저장된다. */
    fun save(claim: Claim): Claim

    fun findByPublicId(publicId: String): Claim?

    /** 셀러 스코프 단건 조회 — 타 셀러 클레임은 없는 것으로 본다. */
    fun findByPublicIdAndSellerId(
        publicId: String,
        sellerId: Long,
    ): Claim?

    fun findAllByOrderId(orderId: Long): List<Claim>

    /** 같은 주문품목에 아직 살아 있는 클레임이 있는지 — 중복 요청 차단. */
    fun existsActiveByOrderItemId(orderItemId: Long): Boolean

    /**
     * 승인됐지만 아직 환불이 집행되지 않은 건. 환불 집행 배치가 쓴다 —
     * 환불 실패(REFUND_FAILED)는 대상이 아니다. 무한 재시도를 막고 재집행은 운영자 판단으로 돌린다.
     */
    fun findRefundPending(limit: Int): List<Claim>

    /** 자동승인 기한이 지난 셀러 미응답 건. 자동승인 배치가 쓴다. */
    fun findAutoApprovable(
        dueBefore: LocalDateTime,
        limit: Int,
    ): List<Claim>

    /** 결론이 나지 않은 클레임이 그 주문에 있는지 — 구매확정 차단 판정. */
    fun existsInProgressByOrderId(orderId: Long): Boolean
}
