package com.aechak.application.order.claim.usecase.query

import java.time.LocalDateTime

/** 클레임 지표 집계 입력. 요청일 범위가 둘 다 null이면 전체 기간이다. */
data class SellerClaimMetricsQuery(
    val sellerId: Long,
    val requestedFrom: LocalDateTime?,
    val requestedToExclusive: LocalDateTime?,
)
