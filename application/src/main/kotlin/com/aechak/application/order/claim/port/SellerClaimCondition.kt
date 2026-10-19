package com.aechak.application.order.claim.port

import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import java.time.LocalDateTime

/**
 * 셀러 클레임 조회 조건. 빈 목록은 "그 축으로 걸지 않는다"는 뜻이다.
 * 셀러 범위는 claims.seller_id 스냅샷 하나로 걸러 주문·주문품목 조인을 타지 않는다.
 */
data class SellerClaimCondition(
    val sellerId: Long,
    val claimTypes: List<ClaimType>,
    val statuses: List<ClaimStatus>,
    val refundStatuses: List<RefundStatus>,
    val requestedFrom: LocalDateTime?,
    val requestedToExclusive: LocalDateTime?,
    val offset: Long,
    val limit: Int,
)
