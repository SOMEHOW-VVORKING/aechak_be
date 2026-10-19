package com.aechak.application.order.usecase.query

import java.time.LocalDateTime

/**
 * 자동확정 대상 스캔 조건. [afterOrderId] 다음 주문 id부터 오름차순 [limit] 건을 받아
 * 회차 안에서 커서를 밀며 끝까지 처리한다.
 */
data class AutoConfirmScanQuery(
    val dueAtOrBefore: LocalDateTime,
    val afterOrderId: Long = 0L,
    val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(limit in LIMIT_MIN..LIMIT_MAX) { "limit은 $LIMIT_MIN~$LIMIT_MAX 범위 안에 있어야 합니다." }
        require(afterOrderId >= 0L) { "afterOrderId는 0 이상이어야 합니다." }
    }

    companion object {
        const val DEFAULT_LIMIT = 200
        const val LIMIT_MIN = 1
        const val LIMIT_MAX = 1000
    }
}
