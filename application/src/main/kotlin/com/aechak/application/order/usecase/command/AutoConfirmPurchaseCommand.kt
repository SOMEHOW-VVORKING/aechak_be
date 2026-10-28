package com.aechak.application.order.usecase.command

import java.time.LocalDateTime

/**
 * 자동확정 1건 요청. [dueAtOrBefore]는 확정 자격 판정의 기준 시각이다 —
 * 스캔과 같은 시각을 넘겨, 스캔 이후 기한이 미뤄진 주문을 확정하지 않게 한다.
 */
data class AutoConfirmPurchaseCommand(
    val orderId: Long,
    val dueAtOrBefore: LocalDateTime,
)
