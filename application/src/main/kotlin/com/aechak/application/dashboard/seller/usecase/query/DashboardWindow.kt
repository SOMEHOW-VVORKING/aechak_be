package com.aechak.application.dashboard.seller.usecase.query

import java.time.LocalDateTime

/** 집계 구간. [current]가 null이면 주문일·요청일 축을 걸지 않는다(전체 기간). */
data class DashboardWindow(
    val current: Range?,
    val previous: Range?,
) {
    data class Range(
        val from: LocalDateTime,
        val toExclusive: LocalDateTime,
    )
}
