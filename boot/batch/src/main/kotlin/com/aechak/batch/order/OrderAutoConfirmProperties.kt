package com.aechak.batch.order

import com.aechak.application.order.usecase.query.AutoConfirmScanQuery
import org.springframework.boot.context.properties.ConfigurationProperties

/** 구매확정 자동 배치 설정. 확정은 비가역이라 [enabled] 기본값은 꺼진 상태다 — 환경별로 켠다. */
@ConfigurationProperties("order.auto-confirm")
data class OrderAutoConfirmProperties(
    val enabled: Boolean = false,
    val notifyLeadDays: Long = DEFAULT_NOTIFY_LEAD_DAYS,
    val scanSize: Int = AutoConfirmScanQuery.DEFAULT_LIMIT,
) {
    init {
        // cron 주기(1일)보다 짧으면 그 사이에 기한이 지나 안내 없이 확정되는 주문이 생긴다
        require(notifyLeadDays > 0L) { "사전 안내 선행 일수는 1 이상이어야 합니다." }
    }

    companion object {
        const val DEFAULT_NOTIFY_LEAD_DAYS = 1L
    }
}
