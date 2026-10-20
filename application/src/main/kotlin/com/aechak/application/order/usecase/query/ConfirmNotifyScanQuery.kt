package com.aechak.application.order.usecase.query

import java.time.LocalDateTime

/**
 * 구매확정 사전 안내 대상 스캔 조건. 기한이 [dueAtAfter] 초과 [dueAtOrBefore] 이하인 구간만 본다 —
 * 하한이 없으면 기한이 지나 자동확정을 기다리는 건까지 안내 대상으로 끌려온다.
 */
data class ConfirmNotifyScanQuery(
    val dueAtAfter: LocalDateTime,
    val dueAtOrBefore: LocalDateTime,
    val afterOrderId: Long = 0L,
    val limit: Int = AutoConfirmScanQuery.DEFAULT_LIMIT,
) {
    init {
        require(dueAtAfter < dueAtOrBefore) { "안내 구간의 하한은 상한보다 앞서야 합니다." }
        require(limit in AutoConfirmScanQuery.LIMIT_MIN..AutoConfirmScanQuery.LIMIT_MAX) {
            "limit은 ${AutoConfirmScanQuery.LIMIT_MIN}~${AutoConfirmScanQuery.LIMIT_MAX} 범위 안에 있어야 합니다."
        }
        require(afterOrderId >= 0L) { "afterOrderId는 0 이상이어야 합니다." }
    }
}
