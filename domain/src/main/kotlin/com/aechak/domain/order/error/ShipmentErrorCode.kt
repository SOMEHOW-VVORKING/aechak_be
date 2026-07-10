package com.aechak.domain.order.error

import com.aechak.common.error.ErrorCode

enum class ShipmentErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {

    SHIPMENT_NOT_FOUND(70001, "배송 정보를 찾을 수 없습니다.", 404),
    SHIPMENT_ALREADY_DELIVERED(70002, "이미 배송완료 처리된 배송입니다.", 409),
}
