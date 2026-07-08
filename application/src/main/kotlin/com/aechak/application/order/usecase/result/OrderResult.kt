package com.aechak.application.order.usecase.result

import com.aechak.domain.order.Order

/** UseCase 반환 전용 모델 골격 — 엔티티 반환 금지. 규칙은 user 템플릿(UserResult) 참조. */
data class OrderResult(
    val orderId: Long,
    // TODO: 기능 확정 시 필드 추가
) {
    companion object {
        fun from(order: Order): OrderResult = OrderResult(order.id)
    }
}
