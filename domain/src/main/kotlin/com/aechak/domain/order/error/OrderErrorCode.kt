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
    ORDER_ACCESS_DENIED(50003, "셀러 주문을 처리할 권한이 없습니다.", 403),
    ORDER_NOT_ACCEPTABLE(50004, "결제완료 상태의 주문만 발주 확인할 수 있습니다.", 409),
    ORDER_NOT_DISPATCHABLE(50005, "발송 대기 상태의 주문만 발송 처리할 수 있습니다.", 409),
    INVALID_ORDER_ITEM_STATUS_TRANSITION(50006, "허용되지 않은 주문품목 상태 전이입니다.", 409),

    // 주문그룹
    INVALID_ORDER_GROUP_STATUS_TRANSITION(50100, "허용되지 않은 주문그룹 상태 전이입니다.", 409),
    INVALID_ORDER_GROUP_AMOUNT(50101, "적립금 사용액은 0원 이상이어야 합니다.", 400),
    POINT_EXCEEDS_PAYABLE_AMOUNT(50102, "적립금은 결제 금액을 초과해 사용할 수 없습니다.", 422),
    POINT_BELOW_MINIMUM_USAGE(50103, "적립금은 1,000원 이상부터 사용할 수 있습니다.", 400),
    ORDER_ITEM_NOT_ORDERABLE(50104, "주문할 수 없는 상품이 포함되어 있습니다.", 409),
    ORDER_PRODUCT_VERSION_NOT_READY(50105, "상품 정보가 준비되지 않아 주문할 수 없습니다.", 409),
    ORDER_AMOUNT_CHANGED(50106, "주문 금액이 변경되었습니다. 주문서를 다시 확인해 주세요.", 409),
    INSUFFICIENT_STOCK(50107, "재고가 부족한 상품이 포함되어 있습니다.", 409),
    ORDER_AMOUNT_BELOW_MINIMUM(50108, "최소 결제 금액에 미치지 못합니다.", 422),
    ORDER_GROUP_NOT_CANCELLABLE(50109, "취소할 수 없는 주문입니다.", 409),

    // 장바구니
    INVALID_CART_ITEM_QUANTITY(50200, "장바구니 담을 수량은 1 이상이어야 합니다.", 400),
    CART_ITEM_OUT_OF_STOCK(50201, "재고가 부족합니다.", 409),
    CART_ITEM_NOT_PURCHASABLE(50202, "상품 상태가 변경되었습니다.", 409),
    CART_ITEM_LIMIT_EXCEEDED(50203, "장바구니에 담을 수 있는 품목 수를 초과했습니다.", 422),
    CART_RATE_LIMIT_EXCEEDED(50204, "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요.", 429),
    CART_ITEM_NOT_FOUND(50205, "장바구니 항목을 찾을 수 없습니다.", 404),
    CART_ITEM_ACCESS_DENIED(50206, "본인의 장바구니 항목만 접근할 수 있습니다.", 403),

    // product지만, BC 침범 방지를 위해..
    CART_ITEM_OPTION_NOT_FOUND(50207, "상품을 찾을 수 없습니다.", 404),

    // 배송
    SHIPMENT_NOT_FOUND(50300, "배송 정보를 찾을 수 없습니다.", 404),
    SHIPMENT_ALREADY_DELIVERED(50301, "이미 배송완료 처리된 배송입니다.", 409),

    // 클레임
    CLAIM_NOT_FOUND(50400, "클레임을 찾을 수 없습니다.", 404),
    CLAIM_NOT_REQUESTABLE(50401, "현재 주문 상태에서는 요청할 수 없는 클레임입니다.", 409),
    INVALID_CLAIM_STATUS_TRANSITION(50402, "허용되지 않은 클레임 상태 전이입니다.", 409),
    CLAIM_ALREADY_IN_PROGRESS(50403, "이미 진행 중인 클레임이 있는 상품입니다.", 409),
    CLAIM_ACCESS_DENIED(50404, "클레임을 처리할 권한이 없습니다.", 403),
    CLAIM_REJECTION_REASON_REQUIRED(50405, "거부 사유를 입력해 주세요.", 400),
    CLAIM_QUANTITY_EXCEEDS_ORDERED(50406, "주문한 수량보다 많이 요청할 수 없습니다.", 422),
    CLAIM_REFUND_FAILED(50407, "환불 처리에 실패했습니다. 잠시 후 다시 확인해 주세요.", 502),
    CLAIM_ALREADY_RESOLVED(50408, "이미 처리된 클레임입니다.", 409),
}
