package com.aechak.domain.order.error

import com.aechak.common.error.ErrorCode

enum class OrderErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    // 주문
    ORDER_NOT_FOUND(50000, "주문을 찾을 수 없습니다.", 404),
    CANNOT_CANCEL_SHIPPED(50001, "배송이 시작된 주문은 취소할 수 없습니다.", 409),
    INVALID_ORDER_STATUS_TRANSITION(50002, "허용되지 않은 주문 상태 전이입니다.", 409),

    // 주문그룹
    INVALID_ORDER_GROUP_STATUS_TRANSITION(50100, "허용되지 않은 주문그룹 상태 전이입니다.", 409),

    // 장바구니
    INVALID_CART_ITEM_QUANTITY(50200, "장바구니 담을 수량은 1 이상이어야 합니다.", 400),
    CART_ITEM_OUT_OF_STOCK(50201, "재고가 부족합니다.", 409),
    CART_ITEM_NOT_PURCHASABLE(50202, "상품 상태가 변경되었습니다.", 409),
    CART_ITEM_LIMIT_EXCEEDED(50203, "장바구니에 담을 수 있는 품목 수를 초과했습니다.", 422),

    // product지만, BC 침범 방지를 위해..
    CART_ITEM_OPTION_NOT_FOUND(50207, "상품을 찾을 수 없습니다.", 404),
    CART_NOT_FOUND(50208, "장바구니를 찾을 수 없습니다.", 404),

    // 배송
    SHIPMENT_NOT_FOUND(50300, "배송 정보를 찾을 수 없습니다.", 404),
    SHIPMENT_ALREADY_DELIVERED(50301, "이미 배송완료 처리된 배송입니다.", 409),
}
