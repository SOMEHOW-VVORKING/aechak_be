package com.aechak.domain.order.error

import com.aechak.common.error.ErrorCode

enum class OrderErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {

    ORDER_NOT_FOUND(50001, "주문을 찾을 수 없습니다.", 404),
    CANNOT_CANCEL_SHIPPED(50002, "배송이 시작된 주문은 취소할 수 없습니다.", 409),
    INVALID_ORDER_STATUS_TRANSITION(50003, "허용되지 않은 주문 상태 전이입니다.", 409),
    INVALID_ORDER_GROUP_STATUS_TRANSITION(50004, "허용되지 않은 주문그룹 상태 전이입니다.", 409),
    INVALID_CART_ITEM_QUANTITY(50005, "장바구니 담을 수량은 1 이상이어야 합니다.", 400),
}
