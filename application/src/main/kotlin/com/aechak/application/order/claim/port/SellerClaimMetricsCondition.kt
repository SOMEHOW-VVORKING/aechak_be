package com.aechak.application.order.claim.port

import java.time.LocalDateTime

/**
 * 셀러 대시보드의 클레임 처리 대기 집계 조건. 단계별 판정은 집계 쿼리가 들고 있어 상태·유형 축을 받지 않고,
 * 페이징 축도 없다 — 목록 조건(SellerClaimCondition)을 빌려 쓰면 쓰지 않는 축을 0으로 채워야 한다.
 * 기간은 요청일(claims.requested_at) 반개구간이다.
 */
data class SellerClaimMetricsCondition(
    val sellerId: Long,
    val requestedFrom: LocalDateTime?,
    val requestedToExclusive: LocalDateTime?,
)
