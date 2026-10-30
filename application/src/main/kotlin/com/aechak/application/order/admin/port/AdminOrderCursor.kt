package com.aechak.application.order.admin.port

import java.time.LocalDateTime

/**
 * 목록 다음 페이지의 시작 앵커. 주문 시각 하나로는 같은 시각에 들어온 두 주문이 갈리지 않아
 * 주문 id를 타이브레이크로 함께 싣는다.
 */
data class AdminOrderCursor(
    val lastOrderedAt: LocalDateTime,
    val lastOrderId: Long,
)
