package com.aechak.batch.order

import com.aechak.application.order.usecase.query.AutoConfirmScanQuery
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 구매확정 자동 배치 설정. 확정은 비가역이라 [enabled] 기본값은 꺼진 상태다 — 환경별로 켠다.
 * [maxRounds]는 커서가 끝까지 전진하지 못하는 상황에서 회차가 무한히 늘어나지 않게 하는 상한이다.
 */
@ConfigurationProperties("order.auto-confirm")
data class OrderAutoConfirmProperties(
    val enabled: Boolean = false,
    val notifyLeadDays: Long = DEFAULT_NOTIFY_LEAD_DAYS,
    val scanSize: Int = AutoConfirmScanQuery.DEFAULT_LIMIT,
    val maxRounds: Int = DEFAULT_MAX_ROUNDS,
) {
    init {
        require(notifyLeadDays > 0L) { "사전 안내 선행 일수는 1 이상이어야 합니다." }
        require(maxRounds > 0) { "회차 상한은 1 이상이어야 합니다." }
    }

    companion object {
        const val DEFAULT_NOTIFY_LEAD_DAYS = 1L
        const val DEFAULT_MAX_ROUNDS = 500
    }
}
