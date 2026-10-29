package com.aechak.application.order.claim.admin.port

import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import java.time.LocalDateTime

/**
 * 어드민 클레임 조회 조건. 빈 목록·null은 "그 축으로 걸지 않는다"는 뜻이다.
 * 셀러 범위로 좁히지 않는다 — 전 셀러의 클레임이 운영자의 작업 목록이다.
 *
 * [openOnly]는 운영자가 지금 개입할 수 있는 건으로 좁히는 축이다 — 상태 축과 함께 주면 AND로 묶인다.
 * [lastClaimId]는 커서가 가리키는 마지막 행이고 정렬 방향에 따라 부등호가 뒤집힌다.
 */
data class AdminClaimCondition(
    val claimTypes: List<ClaimType>,
    val statuses: List<ClaimStatus>,
    val requestedFrom: LocalDateTime?,
    val requestedToExclusive: LocalDateTime?,
    val orderPublicId: String?,
    val sellerKeyword: String?,
    val buyerKeyword: String?,
    val openOnly: Boolean,
    val sort: AdminClaimSort,
    val lastClaimId: Long?,
    val limit: Int,
)
