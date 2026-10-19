package com.aechak.application.dashboard.seller.usecase.query

import java.time.LocalDateTime

/**
 * 집계 구간. [current]가 null이면 주문일·요청일 축을 걸지 않는다(전체 기간).
 * [at]은 이 응답의 기준 시각 — 한 요청의 모든 지표가 같은 시각을 본다. 지표마다 시계를 다시 뜨면
 * 발송 기한 경계의 주문이 현재 기간과 직전 기간에서 다르게 판정된다.
 */
data class DashboardWindow(
    val at: LocalDateTime,
    val current: Range?,
    val previous: Range?,
) {
    data class Range(
        val from: LocalDateTime,
        val toExclusive: LocalDateTime,
    )
}
