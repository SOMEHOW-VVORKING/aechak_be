package com.aechak.application.order.claim.port

import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import java.time.LocalDateTime

/**
 * 셀러 클레임 조회 조건. 빈 목록은 "그 축으로 걸지 않는다"는 뜻이다.
 * 셀러 범위는 claims.seller_id 스냅샷 하나로 걸러 주문·주문품목 조인을 타지 않는다.
 *
 * [pendingStages]는 셀러가 할 일이 남은 단계 축이다 — 상태 축만으로는 회수 중 한 상태에 들어 있는
 * 송장 등록 대기와 완료 처리 대기를 가를 수 없어, 대시보드 처리 대기 집계와 같은 판정을 목록도 쓸 수 있게 둔다.
 * 상태 축과 함께 주면 AND로 묶인다.
 */
data class SellerClaimCondition(
    val sellerId: Long,
    val claimTypes: List<ClaimType>,
    val statuses: List<ClaimStatus>,
    val refundStatuses: List<RefundStatus>,
    val pendingStages: List<SellerClaimPendingStage>,
    val requestedFrom: LocalDateTime?,
    val requestedToExclusive: LocalDateTime?,
    val offset: Long,
    val limit: Int,
)
