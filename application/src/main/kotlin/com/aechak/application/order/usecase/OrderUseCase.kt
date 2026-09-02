package com.aechak.application.order.usecase

import com.aechak.application.order.usecase.result.ReviewOrderItemResult

/**
 * order 도메인의 유일한 진입점 계약. 규칙은 user 도메인 템플릿(UserUseCase) 참조.
 * 입출력 어휘(command/·result/·query/)는 이 패키지 하위에 둔다 — 계약은 usecase/, 구현은 service/.
 */
interface OrderUseCase {
    /** 리뷰 작성 자격 판정용 본인 주문품목 조회. 없거나 본인 주문이 아니면 null. */
    fun getOrderItemForReview(
        orderItemId: Long,
        buyerId: Long,
    ): ReviewOrderItemResult?
}
